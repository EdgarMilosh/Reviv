package lt.reviv.bloodtests.service.analysis;

import lt.reviv.bloodtests.ai.analysis.AnalysisProperties;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalysisQueueRecoveryJob {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AnalysisQueueRecoveryJob.class
            );

    private final BloodTestAnalysisRepository analysisRepository;
    private final BloodTestAnalysisExecutionService executionService;
    private final AnalysisPersistenceService persistenceService;
    private final AnalysisProperties properties;

    public AnalysisQueueRecoveryJob(
            BloodTestAnalysisRepository analysisRepository,
            BloodTestAnalysisExecutionService executionService,
            AnalysisPersistenceService persistenceService,
            AnalysisProperties properties
    ) {
        this.analysisRepository = analysisRepository;
        this.executionService = executionService;
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelay = 30000,
            initialDelay = 10000
    )
    public void recoverQueuedAnalyses() {

        if (!properties.enabled()) {
            return;
        }

        var queued = analysisRepository
                .findTop3ByStatusOrderByCreatedAtAsc(
                        BloodTestAnalysis.Status.QUEUED
                );

        for (var analysis : queued) {

            Long bloodTestId = analysis.getBloodTestId();

            try {
                log.info(
                        "Recovering queued analysis. Blood test ID: {}",
                        bloodTestId
                );

                boolean executed =
                        executionService.execute(bloodTestId);

                if (executed) {
                    log.info(
                            "Analysis completed. Blood test ID: {}",
                            bloodTestId
                    );
                }

            } catch (RuntimeException e) {

                log.error(
                        "Queued analysis failed. Blood test ID: {}. Error: {}",
                        bloodTestId,
                        e.getClass().getSimpleName()
                );

                try {
                    persistenceService.failQueued(bloodTestId);
                } catch (RuntimeException persistenceError) {
                    log.error(
                            "Failed to update analysis state. Blood test ID: {}",
                            bloodTestId
                    );
                }
            }
        }
    }
}
