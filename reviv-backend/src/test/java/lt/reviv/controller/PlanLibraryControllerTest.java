package lt.reviv.controller;

import lt.reviv.bloodtests.controller.plan.PlanLibraryController;
import lt.reviv.bloodtests.service.plan.ConsumptionPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanLibraryControllerTest {
    private final ConsumptionPlanService service = mock(ConsumptionPlanService.class);
    private final PlanLibraryController controller = new PlanLibraryController(service);
    private final Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("user-a").build();

    @Test
    void listDelegatesCurrentJwt() {
        when(service.listMine(jwt)).thenReturn(List.of());
        assertEquals(List.of(), controller.list(jwt));
        verify(service).listMine(jwt);
    }

    @Test
    void getDelegatesPlanIdAndCurrentJwt() {
        var result = mock(ConsumptionPlanService.PlanResponse.class);
        when(service.getByPlanId(7L, jwt)).thenReturn(result);
        assertSame(result, controller.get(7L, jwt));
        verify(service).getByPlanId(7L, jwt);
    }

    @Test
    void updateDelegatesPlanAndEntryWithoutUsingLatestGeneration() {
        var request = new ConsumptionPlanService.EditRequest(
                lt.reviv.bloodtests.domain.plan.PlanEntry.DayPart.MORNING,
                java.time.LocalTime.of(8, 0),
                lt.reviv.bloodtests.domain.plan.PlanEntry.MealRelation.AFTER_MEAL,
                java.time.LocalDate.of(2026, 12, 1), java.time.LocalDate.of(2026, 12, 20), null);
        controller.update(7L, 3L, request, jwt);
        verify(service).updateByPlanId(7L, 3L, request, jwt);
    }

    @Test
    void activateDelegatesCurrentUser() {
        controller.activate(7L, jwt);
        verify(service).activateByPlanId(7L, jwt);
    }
}
