package lt.reviv.bloodtests.service.rec;

import lt.reviv.bloodtests.ai.rec.OllamaProperties;
import lt.reviv.bloodtests.ai.rec.OllamaRecommendationClient;
import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.repository.rec.RecommendationGenerationRepository;
import lt.reviv.bloodtests.repository.rec.SupplementRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationPersistenceService {

    private final RecommendationGenerationRepository generationRepository;
    private final SupplementRecommendationRepository recommendationRepository;
    private final OllamaProperties properties;
    private final JsonMapper mapper;

    public RecommendationPersistenceService(
            RecommendationGenerationRepository generationRepository,
            SupplementRecommendationRepository recommendationRepository,
            OllamaProperties properties,
            JsonMapper mapper
    ) {
        this.generationRepository = generationRepository;
        this.recommendationRepository = recommendationRepository;
        this.properties = properties;
        this.mapper = mapper;
    }

    /**
     * Issaugo viena AI generavimo rezultata ir visus jo sprendimus vienoje DB transakcijoje.
     * Issaugotas PENDING nera medicininis patvirtinimas ir vartojimo plano neaktyvuoja.
     */
    @Transactional
    public Long save(
            Long bloodTestId,
            RecommendationContextService.RecommendationContext context,
            List<OllamaRecommendationClient.Proposal> proposals,
            RecommendationPreviewService.PreviewResponse preview
    ) {
        if (context.criticalDetected() || proposals == null || proposals.isEmpty()) {
            return null;
        }

        Map<Long, RecommendationPreviewService.RecommendationItem> suggested =
                preview.recommendations().stream().collect(Collectors.toMap(
                        RecommendationPreviewService.RecommendationItem::indicatorId,
                        Function.identity()
                ));

        Map<Long, RecommendationPreviewService.NotRecommendedItem> notSuggested =
                preview.notRecommended().stream().collect(Collectors.toMap(
                        RecommendationPreviewService.NotRecommendedItem::indicatorId,
                        Function.identity()
                ));

        if (suggested.size() + notSuggested.size() != proposals.size()) {
            throw new IllegalStateException("Neatitinka Ollama sprendimu ir perziuros irasu skaicius.");
        }

        var generation = generationRepository.save(new RecommendationGeneration(
                bloodTestId,
                properties.model(),
                asJson(context.profile()),
                context.criticalityNotAssessed()
        ));

        List<SupplementRecommendation> items = new ArrayList<>();

        for (var proposal : proposals) {
            var status = SupplementRecommendation.RecommendationStatus.valueOf(
                    proposal.status()
            );

            if (status == SupplementRecommendation.RecommendationStatus.SUGGESTED) {
                var item = suggested.get(proposal.indicatorId());
                if (item == null) {
                    throw new IllegalStateException("Truksta pasiulyto papildo perziuros iraso.");
                }
                items.add(new SupplementRecommendation(
                        generation.getId(),
                        item.indicatorId(),
                        item.supplementId(),
                        item.supplementName(),
                        status,
                        item.proposedDailyDose(),
                        item.doseUnit(),
                        item.durationDays(),
                        item.reason(),
                        asJson(item.warnings()),
                        proposal.needsSpecialistReview()
                ));
            } else {
                var item = notSuggested.get(proposal.indicatorId());
                if (item == null || !item.status().equals(status.name())) {
                    throw new IllegalStateException("Truksta nesiulomo papildo perziuros iraso.");
                }
                items.add(new SupplementRecommendation(
                        generation.getId(),
                        item.indicatorId(),
                        null,
                        null,
                        status,
                        null,
                        null,
                        null,
                        item.reason(),
                        asJson(item.warnings()),
                        proposal.needsSpecialistReview()
                ));
            }
        }

        recommendationRepository.saveAll(items);
        return generation.getId();
    }

    private String asJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Nepavyko issaugoti rekomendacijos JSON.", e);
        }
    }
}
