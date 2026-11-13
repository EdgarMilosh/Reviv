package lt.reviv.bloodtests.service.analysis;

import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.domain.IndicatorExplanation;
import lt.reviv.bloodtests.dto.analysis.AnalysisResultResponse;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.IndicatorExplanationRepository;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BloodTestAnalysisReadService {

    private final BloodTestReviewService reviewService;
    private final BloodTestAnalysisService analysisService;
    private final BloodTestAnalysisRepository analysisRepository;
    private final IndicatorExplanationRepository explanationRepository;
    private final JsonMapper mapper;

    public BloodTestAnalysisReadService(
            BloodTestReviewService reviewService,
            BloodTestAnalysisService analysisService,
            BloodTestAnalysisRepository analysisRepository,
            IndicatorExplanationRepository explanationRepository,
            JsonMapper mapper
    ) {
        this.reviewService = reviewService;
        this.analysisService = analysisService;
        this.analysisRepository = analysisRepository;
        this.explanationRepository = explanationRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AnalysisResultResponse getAnalysis(
            Long bloodTestId,
            Jwt jwt
    ) {
        var details = reviewService.getDetails(bloodTestId, jwt);

        if (!"CONFIRMED".equals(details.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tyrimas dar nepatvirtintas."
            );
        }

        var analysis = analysisRepository
                .findByBloodTestId(bloodTestId);

        String status = analysis
                .map(a -> a.getStatus().name())
                .orElse("NOT_REQUESTED");

        String model = analysis
                .map(BloodTestAnalysis::getAiModel)
                .orElse(null);

        Map<Long, IndicatorExplanation> saved = new HashMap<>();

        if (analysis.isPresent()
                && analysis.get().getStatus()
                == BloodTestAnalysis.Status.COMPLETED) {

            var explanations = explanationRepository
                    .findByAnalysisIdOrderByIdAsc(
                            analysis.get().getId()
                    );

            for (var explanation : explanations) {
                saved.put(
                        explanation.getIndicatorId(),
                        explanation
                );
            }
        }

        var prepared = analysisService.prepare(bloodTestId);

        var indicators = new ArrayList<
                BloodTestAnalysisService.AnalyzedIndicator>();

        indicators.addAll(prepared.critical());
        indicators.addAll(prepared.forAi());
        indicators.addAll(prepared.unknown());

        var result = new ArrayList<
                AnalysisResultResponse.IndicatorResult>();

        for (var indicator : indicators) {

            var explanation = saved.get(indicator.indicatorId());

            result.add(
                    new AnalysisResultResponse.IndicatorResult(
                            indicator.indicatorId(),
                            indicator.name(),
                            indicator.rawValue(),
                            indicator.unit(),
                            indicator.referenceInterval(),
                            indicator.status().name(),
                            indicator.deviationPercent(),
                            indicator.criticalStatus().name(),
                            explanation == null
                                    ? null
                                    : explanation.getMeaning(),
                            explanation == null
                                    ? List.of()
                                    : parseCauses(
                                    explanation.getPossibleCausesJson()
                            ),
                            explanation == null
                                    ? null
                                    : explanation.getPracticalAdvice()
                    )
            );
        }

        return new AnalysisResultResponse(
                bloodTestId,
                status,
                model,
                prepared.normalCount(),
                List.copyOf(result)
        );
    }

    private List<String> parseCauses(String json) {
        try {
            JsonNode node = mapper.readTree(json);

            if (!node.isArray()) {
                throw new IllegalStateException(
                        "Netinkamas priezasciu JSON formatas."
                );
            }

            List<String> causes = new ArrayList<>();

            for (JsonNode item : node) {
                if (item.isTextual()) {
                    causes.add(item.asText());
                }
            }

            return List.copyOf(causes);

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Nepavyko nuskaityti DI paaiskinimo.",
                    e
            );
        }
    }
}
