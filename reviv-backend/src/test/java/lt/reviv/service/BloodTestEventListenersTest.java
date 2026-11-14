package lt.reviv.service;

import lt.reviv.bloodtests.service.*;
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
