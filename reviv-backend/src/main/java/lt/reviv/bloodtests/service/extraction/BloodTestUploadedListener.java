package lt.reviv.bloodtests.service.extraction;

import lombok.RequiredArgsConstructor;
import lt.reviv.bloodtests.service.upload.BloodTestUploadedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BloodTestUploadedListener {

    private final BloodTestExtractionService extractionService;

    @Async("pdfExtractionExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(BloodTestUploadedEvent event) {

        extractionService.process(event.bloodTestId());
    }
}
