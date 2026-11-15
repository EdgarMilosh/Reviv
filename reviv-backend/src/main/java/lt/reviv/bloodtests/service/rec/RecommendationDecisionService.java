package lt.reviv.bloodtests.service.rec;

import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.repository.rec.RecommendationGenerationRepository;
import lt.reviv.bloodtests.repository.rec.SupplementRecommendationRepository;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RecommendationDecisionService {

    public record ItemResult(
            Long id,
            Long indicatorId,
            Long supplementId,
            String supplementName,
            String recommendationStatus,
            String decision,
            BigDecimal proposedDailyDose,
            String doseUnit,
            Integer durationDays,
            String reason,
            List<String> warnings,
            boolean needsSpecialistReview,
            OffsetDateTime decidedAt
    ) {}

    public record LatestResult(
            Long generationId,
            Long bloodTestId,
            OffsetDateTime generatedAt,
            boolean criticalityNotAssessed,
            boolean medicallyVerified,
            List<ItemResult> items
    ) {}

    private final BloodTestReviewService reviewService;
    private final RecommendationGenerationRepository generationRepository;
    private final SupplementRecommendationRepository recommendationRepository;
    private final JsonMapper mapper;

    public RecommendationDecisionService(
            BloodTestReviewService reviewService,
            RecommendationGenerationRepository generationRepository,
            SupplementRecommendationRepository recommendationRepository,
            JsonMapper mapper
    ) {
        this.reviewService = reviewService;
        this.generationRepository = generationRepository;
        this.recommendationRepository = recommendationRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public LatestResult latest(Long bloodTestId, Jwt jwt) {
        checkOwnerAndConfirmation(bloodTestId, jwt);
        RecommendationGeneration generation = latestGeneration(bloodTestId);
        var items = recommendationRepository.findByGenerationIdOrderByIdAsc(generation.getId())
                .stream()
                .map(this::toItemResult)
                .toList();
        return new LatestResult(
                generation.getId(),
                bloodTestId,
                generation.getCreatedAt(),
                generation.isCriticalityNotAssessed(),
                false, // naudotojo pasirinkimas nera medicininis patvirtinimas
                items
        );
    }

    @Transactional
    public ItemResult decide(
            Long bloodTestId,
            Long recommendationId,
            SupplementRecommendation.Decision decision,
            Jwt jwt
    ) {
        checkOwnerAndConfirmation(bloodTestId, jwt);

        if (decision != SupplementRecommendation.Decision.ACCEPTED
                && decision != SupplementRecommendation.Decision.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Galimi tik sprendimai ACCEPTED arba REJECTED."
            );
        }

        // Sprendimai leidziami tik naujausio sio tyrimo generavimo irasams.
        RecommendationGeneration latest = latestGeneration(bloodTestId);
        SupplementRecommendation item = recommendationRepository
                .findByIdAndGenerationId(recommendationId, latest.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sio tyrimo naujausiame generavime rekomendacija nerasta."
                ));

        if (item.getRecommendationStatus()
                != SupplementRecommendation.RecommendationStatus.SUGGESTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Priimti arba atmesti galima tik pasiulyta papilda."
            );
        }
        if (item.getDecision() != SupplementRecommendation.Decision.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Del sio pasiulymo jau priimtas sprendimas."
            );
        }

        // Tai TIK naudotojo pasirinkimas. Doziu saugumo cia netvirtiname.
        item.decide(decision);
        recommendationRepository.saveAndFlush(item);
        return toItemResult(item);
    }

    private void checkOwnerAndConfirmation(Long bloodTestId, Jwt jwt) {
        // getDetails tikrina, ar tyrimas priklauso JWT naudotojui.
        var details = reviewService.getDetails(bloodTestId, jwt);
        if (!"CONFIRMED".equals(details.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tyrimas dar nepatvirtintas."
            );
        }
    }

    private RecommendationGeneration latestGeneration(Long bloodTestId) {
        return generationRepository
                .findByBloodTestIdOrderByCreatedAtDescIdDesc(bloodTestId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Rekomendaciju siam tyrimui dar nera."
                ));
    }

    private ItemResult toItemResult(SupplementRecommendation item) {
        return new ItemResult(
                item.getId(),
                item.getIndicatorId(),
                item.getSupplementId(),
                item.getSupplementNameSnapshot(),
                item.getRecommendationStatus().name(),
                item.getDecision().name(),
                item.getProposedDailyDose(),
                item.getDoseUnit(),
                item.getDurationDays(),
                item.getReason(),
                parseWarnings(item.getRiskWarningsJson()),
                item.isNeedsSpecialistReview(),
                item.getDecidedAt()
        );
    }

    private List<String> parseWarnings(String warningsJson) {
        if (warningsJson == null || warningsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = mapper.readValue(
                    warningsJson,
                    new TypeReference<List<String>>() {}
            );
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "Sugadintas issaugotu rekomendaciju ispejimu JSON.", ex
            );
        }
    }
}
