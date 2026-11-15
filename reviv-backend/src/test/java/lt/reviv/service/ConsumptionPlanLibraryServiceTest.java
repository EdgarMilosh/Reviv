package lt.reviv.service;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.bloodtests.repository.plan.PlanListRow;
import lt.reviv.bloodtests.service.plan.ConsumptionPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ConsumptionPlanLibraryServiceTest {
    private final ConsumptionPlanRepository plans = mock(ConsumptionPlanRepository.class);
    private final PlanEntryRepository entries = mock(PlanEntryRepository.class);
    private final ConsumptionPlanService service = new ConsumptionPlanService(
            null, null, null, null, null, null, plans, entries, null,
            null, null, null, null);
    private final Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
            .subject("google-user-a").build();

    @Test
    void listReturnsOnlyPlansFetchedForCurrentUserAndTheirSupplements() {
        var now = OffsetDateTime.now();
        var row = new PlanListRow(12L, 4L, ConsumptionPlan.Status.ACTIVE, now, now,
                "blood-test.pdf", "Lab A", LocalDate.of(2026, 11, 10));
        var item = mock(PlanEntry.class);
        when(item.getPlanId()).thenReturn(12L);
        when(item.getSupplementNameSnapshot()).thenReturn("Vitaminas D3");
        when(item.getStartDate()).thenReturn(LocalDate.of(2026, 11, 15));
        when(item.getEndDate()).thenReturn(LocalDate.of(2026, 12, 15));
        when(plans.findAllForUser("google-user-a")).thenReturn(List.of(row));
        when(entries.findByPlanIdInOrderByPlanIdAscIdAsc(List.of(12L))).thenReturn(List.of(item));

        var result = service.listMine(jwt);
        assertEquals(1, result.size());
        assertEquals(12L, result.getFirst().id());
        assertEquals("ACTIVE", result.getFirst().status());
        assertEquals(List.of("Vitaminas D3"), result.getFirst().supplements());
        assertEquals(LocalDate.of(2026, 11, 15), result.getFirst().startDate());
        verify(plans).findAllForUser("google-user-a");
        verify(entries).findByPlanIdInOrderByPlanIdAscIdAsc(List.of(12L));
    }

    @Test
    void listDoesNotQueryEntriesWhenNoPlansExist() {
        when(plans.findAllForUser("google-user-a")).thenReturn(List.of());
        assertTrue(service.listMine(jwt).isEmpty());
        verifyNoInteractions(entries);
    }

    @Test
    void directAccessRequiresPlanOwnership() {
        when(plans.findOwnedById(2L, "google-user-a")).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class,
                () -> service.getByPlanId(2L, jwt));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(plans).findOwnedById(2L, "google-user-a");
    }
}
