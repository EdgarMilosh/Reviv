package lt.reviv.controller;

import lt.reviv.doses.controller.DoseLogController;
import lt.reviv.doses.domain.DoseLog;
import lt.reviv.doses.dto.DaySchedule;
import lt.reviv.doses.dto.HistorySummary;
import lt.reviv.doses.dto.MarkRequest;
import lt.reviv.doses.dto.MarkResponse;
import lt.reviv.doses.service.DoseLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-13: dose tracking controller (DP4 - plan controller). */
@ExtendWith(MockitoExtension.class)
class DoseLogControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    @Mock
    private DoseLogService service;

    @InjectMocks
    private DoseLogController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token").header("alg", "none").subject("google-1").build();
    }

    @Test
    void TA50_scheduleDelegatesToService() {
        Jwt jwt = jwt();
        DaySchedule schedule = new DaySchedule(DAY, 10L, "ACTIVE", List.of());
        when(service.schedule(10L, DAY, jwt)).thenReturn(schedule);

        assertSame(schedule, controller.schedule(10L, DAY, jwt));
    }

    @Test
    void TA50_markDelegatesTheRequestedStatus() {
        Jwt jwt = jwt();
        MarkResponse response = new MarkResponse(null, null);
        when(service.mark(10L, 1L, DAY, DoseLog.Status.SKIPPED, jwt)).thenReturn(response);

        assertSame(response, controller.mark(10L, 1L, DAY, new MarkRequest(DoseLog.Status.SKIPPED), jwt));
    }

    @Test
    void TA69_cancelDelegatesToService() {
        Jwt jwt = jwt();

        controller.cancel(10L, 1L, DAY, jwt);

        verify(service).cancel(10L, 1L, DAY, jwt);
    }

    @Test
    void TA67_historyDelegatesPeriodToService() {
        Jwt jwt = jwt();
        HistorySummary summary = new HistorySummary(10L, DAY, DAY, 0, 0, 0, 0, null, false, false, null, List.of());
        when(service.history(10L, DAY, DAY, jwt)).thenReturn(summary);

        assertSame(summary, controller.history(10L, DAY, DAY, jwt));
    }
}