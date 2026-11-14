package lt.reviv.bloodtests.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BloodTestAnalysisRequestedListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    BloodTestAnalysisRequestedListener.class
            );

    private final BloodTestAnalysisExecutionService executionService;

    public BloodTestAnalysisRequestedListener(
            BloodTestAnalysisExecutionService executionService
    ) {
        this.executionService = executionService;
    }

    @Async("analysisExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(BloodTestAnalysisRequestedEvent event) {

        try {
            executionService.execute(event.bloodTestId());

        } catch (RuntimeException e) {

            log.error(
                    "US-7 analysis failed for blood test {}",
                    event.bloodTestId(),
                    e
            );
        }
    }
}
