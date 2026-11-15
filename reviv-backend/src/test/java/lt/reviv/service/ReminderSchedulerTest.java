package lt.reviv.service;

import lt.reviv.push.service.ReminderDispatchService;
import lt.reviv.push.service.ReminderScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-12: the scheduler calls the delivery logic and never dies because of a failed run. */
@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock
    private ReminderDispatchService dispatchService;

    @InjectMocks
    private ReminderScheduler scheduler;

    @Test
    void TA49_tickRunsTheDispatchLogic() {
        when(dispatchService.dispatchDue()).thenReturn(3);

        scheduler.tick();

        verify(dispatchService).dispatchDue();
    }

    @Test
    void TA60_tickSurvivesFailedRun() {
        when(dispatchService.dispatchDue()).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> scheduler.tick());
    }
}