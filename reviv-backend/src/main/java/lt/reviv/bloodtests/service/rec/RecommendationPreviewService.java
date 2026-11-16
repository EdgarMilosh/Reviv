package lt.reviv.bloodtests.service.rec;

import lt.reviv.bloodtests.ai.rec.OllamaRecommendationClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationPreviewService {

    private final RecommendationContextService contextService;
    private final OllamaRecommendationClient ollamaClient;
    private final RecommendationPersistenceService persistenceService;

    public RecommendationPreviewService(
            RecommendationContextService contextService,
            OllamaRecommendationClient ollamaClient,
            RecommendationPersistenceService persistenceService
    ) {
        this.contextService = contextService;
        this.ollamaClient = ollamaClient;
        this.persistenceService = persistenceService;
    }

    public record RecommendationItem(
            Long indicatorId,
            String indicatorName,
            Long supplementId,
            String supplementName,
            String activeSubstance,
            String form,
            BigDecimal proposedDailyDose,
            String doseUnit,
            Integer durationDays,
            String reason,
            List<String> warnings
    ) {}


    public record NotRecommendedItem(
            Long indicatorId,
            String indicatorName,
            String status,
            String reason,
            List<String> warnings
    ) {}


    public record PreviewResponse(
            Long bloodTestId,
            boolean criticalDetected,
            boolean criticalityNotAssessed,
            int normalCount,
            int undeterminedCount,
            boolean canAccept,
            List<RecommendationItem> recommendations,
            List<NotRecommendedItem> notRecommended,
            List<String> notices
    ) {}

    public PreviewResponse generate(Long bloodTestId, Jwt jwt) {

        var context = contextService.prepare(bloodTestId, jwt);
        List<String> notices = new ArrayList<>();


        if (context.criticalDetected()) {
            return new PreviewResponse(
                    bloodTestId,
                    true,
                    context.criticalityNotAssessed(),
                    context.normalCount(),
                    context.undeterminedCount(),
                    false,
                    List.of(),  // recommendations
                    List.of(),  // notRecommended
                    List.of(
                            "Nustatytas kritinis rodiklio nukrypimas. "
                                    + "Rekomendacijos negeneruojamos. "
                                    + "Reikalingas skubus medicininis įvertinimas."
                    )          // notices
            );
        }


        if (context.criticalityNotAssessed()) {
            notices.add(
                    "Ne visų rodiklių kritinius nukrypimus sistema gali "
                            + "automatiškai įvertinti. Rekomendacijos yra informacinės."
            );
        }

        notices.add(
                "Tai nepatvirtinti DI demonstraciniai pasiūlymai. "
                        + "Dozavimas, alergijos ir sąveikos dar turi būti "
                        + "patikrinti. Tai nėra gydymo paskyrimas."
        );

        if (context.indicators().isEmpty()) {
            notices.add(
                    "Nėra rodiklių žemiau referencinio intervalo, "
                            + "kuriems galima generuoti pasiūlymus."
            );

            return response(bloodTestId, context, List.of(), List.of(), notices);
        }

        if (context.catalog().isEmpty()) {
            notices.add("Aktyvių papildų katalogas tuščias.");

            return response(bloodTestId, context, List.of(), List.of(), notices);
        }

        var proposals = ollamaClient.suggest(
                context.indicators(),
                context.catalog(),
                context.interactions(),
                context.profile()
        );

        Map<Long, String> indicatorNames = context.indicators()
                .stream()
                .collect(Collectors.toMap(
                        OllamaRecommendationClient.IndicatorInput::indicatorId,
                        OllamaRecommendationClient.IndicatorInput::name
                ));

        var supplements = context.catalog()
                .stream()
                .collect(Collectors.toMap(
                        OllamaRecommendationClient.SupplementInput::supplementId,
                        Function.identity()
                ));

        List<RecommendationItem> result = new ArrayList<>();
        List<NotRecommendedItem> notRecommended = new ArrayList<>();

        for (var proposal : proposals) {

            if (!"SUGGESTED".equals(proposal.status())) {

                List<String> riskWarnings = new ArrayList<>();

                if (proposal.riskWarnings() != null) {
                    proposal.riskWarnings().stream()
                            .filter(w -> w != null && !w.isBlank())
                            .limit(10)
                            .forEach(riskWarnings::add);
                }

                if (proposal.needsSpecialistReview()) {
                    riskWarnings.add("Reikalinga specialisto peržiūra.");
                }

                notRecommended.add(new NotRecommendedItem(
                        proposal.indicatorId(),
                        indicatorNames.get(proposal.indicatorId()),
                        proposal.status(),
                        proposal.reason(),
                        List.copyOf(riskWarnings)
                ));

                continue;
            }

            var supplement = supplements.get(proposal.supplementId());

            if (supplement == null) {
                throw new IllegalStateException(
                        "Ollama grąžino nežinomą papildo ID."
                );
            }

            List<String> warnings = new ArrayList<>();

            if (proposal.riskWarnings() != null) {
                proposal.riskWarnings().stream()
                        .filter(w -> w != null && !w.isBlank())
                        .limit(10)
                        .forEach(warnings::add);
            }

            if (proposal.needsSpecialistReview()) {
                warnings.add(
                        "Reikalinga specialisto peržiūra."
                );
            }

            BigDecimal dose = proposal.proposedDailyDose();
            String unit = proposal.doseUnit();

            if (dose != null) {
                if (dose.signum() <= 0) {
                    dose = null;
                    warnings.add("Netinkama DI pasiūlyta dozė.");
                } else if (unit == null ||
                        !unit.trim().equalsIgnoreCase(supplement.doseUnit())) {
                    dose = null;
                    warnings.add(
                            "Dozės vienetas neatitinka katalogo."
                    );
                } else if (supplement.maxDailyDose() == null) {
                    dose = null;
                    warnings.add(
                            "Kataloge nenurodyta maksimali paros dozė."
                    );
                } else if (dose.compareTo(supplement.maxDailyDose()) > 0) {
                    dose = null;
                    warnings.add(
                            "DI pasiūlyta dozė viršija katalogo ribą."
                    );
                }
            }

            if (dose == null) {
                unit = null;
            }

            Integer duration = proposal.durationDays();

            if (duration != null &&
                    (duration < 1 || duration > 365)) {
                duration = null;
                warnings.add("Netinkama siūloma trukmė.");
            }

            warnings.add(
                    "Dozė ir trukmė nėra mediciniškai patvirtintos."
            );

            result.add(new RecommendationItem(
                    proposal.indicatorId(),
                    indicatorNames.get(proposal.indicatorId()),
                    supplement.supplementId(),
                    supplement.name(),
                    supplement.activeSubstance(),
                    supplement.form(),
                    dose,
                    unit,
                    duration,
                    proposal.reason(),
                    List.copyOf(warnings)
            ));
        }

        if (result.isEmpty() && notRecommended.isEmpty()) {
            notices.add(
                    "Ollama nepateikė papildų rekomendacijų."
            );
        }

        PreviewResponse preview = response(
                bloodTestId,
                context,
                result,
                notRecommended,
                notices
        );

        persistenceService.save(
                bloodTestId,
                context,
                proposals,
                preview
        );

        return preview;
    }


    private PreviewResponse response(
            Long bloodTestId,
            RecommendationContextService.RecommendationContext context,
            List<RecommendationItem> recommendations,
            List<NotRecommendedItem> notRecommended,
            List<String> notices
    ) {
        return new PreviewResponse(
                bloodTestId,
                context.criticalDetected(),
                context.criticalityNotAssessed(),
                context.normalCount(),
                context.undeterminedCount(),
                false,
                List.copyOf(recommendations),
                List.copyOf(notRecommended),
                List.copyOf(notices)
        );
    }

}
