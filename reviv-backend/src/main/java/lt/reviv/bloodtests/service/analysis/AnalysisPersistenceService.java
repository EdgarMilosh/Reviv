package lt.reviv.bloodtests.service.analysis;

import lt.reviv.bloodtests.ai.analysis.OpenAiAnalysisClient;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.domain.IndicatorExplanation;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.IndicatorExplanationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnalysisPersistenceService {

    private final BloodTestAnalysisRepository analysisRepository;
    private final IndicatorExplanationRepository explanationRepository;
    private final JsonMapper mapper;

    public AnalysisPersistenceService(
            BloodTestAnalysisRepository analysisRepository,
            IndicatorExplanationRepository explanationRepository,
            JsonMapper mapper
    ) {
        this.analysisRepository = analysisRepository;
        this.explanationRepository = explanationRepository;
        this.mapper = mapper;
    }


    @Transactional
    public boolean start(
            Long bloodTestId,
            String model,
            String profileSnapshot
    ) {
        int updated = analysisRepository.claimQueued(
                bloodTestId,
                model,
                profileSnapshot
        );

        return updated == 1;
    }


    @Transactional
    public void complete(
            Long bloodTestId,
            List<OpenAiAnalysisClient.Explanation> explanations
    ) {
        BloodTestAnalysis analysis = analysisRepository
                .findByBloodTestId(bloodTestId)
                .orElseThrow();

        if (analysis.getStatus()
                != BloodTestAnalysis.Status.PROCESSING) {
            throw new IllegalStateException(
                    "Analizė nėra vykdoma."
            );
        }

        List<IndicatorExplanation> entities =
                new ArrayList<>();

        for (var explanation : explanations) {

            String causesJson;

            try {
                causesJson = mapper.writeValueAsString(
                        explanation.possibleCauses()
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Nepavyko išsaugoti DI paaiškinimo.",
                        e
                );
            }

            entities.add(new IndicatorExplanation(
                    analysis.getId(),
                    explanation.indicatorId(),
                    explanation.meaning(),
                    causesJson,
                    explanation.practicalAdvice()
            ));
        }

        explanationRepository.saveAll(entities);

        analysis.markCompleted();
    }

    @Transactional
    public void fail(Long bloodTestId) {
        analysisRepository.findByBloodTestId(bloodTestId)
                .ifPresent(analysis -> {
                    if (analysis.getStatus()
                            == BloodTestAnalysis.Status.PROCESSING) {
                        analysis.markFailed();
                    }
                });
    }

    @Transactional
    public void failQueued(Long bloodTestId) {
        analysisRepository.markQueuedAsFailed(bloodTestId);
    }
}
