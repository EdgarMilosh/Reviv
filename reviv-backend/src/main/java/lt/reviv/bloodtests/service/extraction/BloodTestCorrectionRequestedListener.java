
package lt.reviv.bloodtests.service.extraction;

import lt.reviv.bloodtests.service.review.BloodTestCorrectionRequestedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BloodTestCorrectionRequestedListener {

    private final BloodTestCorrectionService correctionService;

    public BloodTestCorrectionRequestedListener(
            BloodTestCorrectionService correctionService
    ) {
        this.correctionService = correctionService;
    }

    @Async("pdfExtractionExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            BloodTestCorrectionRequestedEvent event
    ) {
        correctionService.processCorrections(
                event.bloodTestId()
        );
    }
}
