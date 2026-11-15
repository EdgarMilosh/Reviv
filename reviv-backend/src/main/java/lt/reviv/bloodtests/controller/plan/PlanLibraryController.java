package lt.reviv.bloodtests.controller.plan;

import jakarta.validation.Valid;
import lt.reviv.bloodtests.service.plan.ConsumptionPlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Direct plan navigation independently of the latest US-9 generation. */
@RestController
@RequestMapping("/api/planai")
public class PlanLibraryController {
    private final ConsumptionPlanService service;

    public PlanLibraryController(ConsumptionPlanService service) { this.service = service; }

    @GetMapping
    public List<ConsumptionPlanService.PlanSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return service.listMine(jwt);
    }

    @GetMapping("/{planId}")
    public ConsumptionPlanService.PlanResponse get(@PathVariable Long planId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return service.getByPlanId(planId, jwt);
    }

    @PatchMapping("/{planId}/entries/{entryId}")
    public ConsumptionPlanService.PlanResponse update(@PathVariable Long planId, @PathVariable Long entryId,
            @Valid @RequestBody ConsumptionPlanService.EditRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.updateByPlanId(planId, entryId, request, jwt);
    }

    @PostMapping("/{planId}/activate")
    public ConsumptionPlanService.PlanResponse activate(@PathVariable Long planId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return service.activateByPlanId(planId, jwt);
    }
}
