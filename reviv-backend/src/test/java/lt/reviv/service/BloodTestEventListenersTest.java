package lt.reviv.service;

import lt.reviv.bloodtests.service.*;
import lt.reviv.bloodtests.service.extraction.BloodTestCorrectionRequestedListener;
import lt.reviv.bloodtests.service.extraction.BloodTestCorrectionService;
import lt.reviv.bloodtests.service.extraction.BloodTestExtractionService;
import lt.reviv.bloodtests.service.extraction.BloodTestUploadedListener;
import lt.reviv.bloodtests.service.review.BloodTestCorrectionRequestedEvent;
import lt.reviv.bloodtests.service.upload.BloodTestUploadedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class BloodTestEventListenersTest {
    @Test void uploadedListenerDelegatesToExtractionService() {
        BloodTestExtractionService worker = mock(BloodTestExtractionService.class);
        var listener = new BloodTestUploadedListener(worker);
        listener.handle(new BloodTestUploadedEvent(6L));
        verify(worker).process(6L);
    }

    @Test void correctionListenerDelegatesToCorrectionService() {
        BloodTestCorrectionService worker = mock(BloodTestCorrectionService.class);
        var listener = new BloodTestCorrectionRequestedListener(worker);
        listener.handle(new BloodTestCorrectionRequestedEvent(6L));
        verify(worker).processCorrections(6L);
    }
}
