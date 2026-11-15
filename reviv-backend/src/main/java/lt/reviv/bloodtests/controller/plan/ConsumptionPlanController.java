package lt.reviv.bloodtests.controller.plan;

import jakarta.validation.Valid;
import lt.reviv.bloodtests.service.plan.ConsumptionPlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tyrimai/{id}/plan")
public class ConsumptionPlanController {
    private final ConsumptionPlanService service;

    public ConsumptionPlanController(ConsumptionPlanService service) { this.service = service; }

    @GetMapping
    public ConsumptionPlanService.PlanResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.latest(id, jwt);
    }

    @PostMapping("/generate")
    public ConsumptionPlanService.PlanResponse generate(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.generate(id, jwt);
    }

    @PatchMapping("/entries/{entryId}")
    public ConsumptionPlanService.PlanResponse update(@PathVariable Long id, @PathVariable Long entryId,
                                                       @Valid @RequestBody ConsumptionPlanService.EditRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, entryId, request, jwt);
    }

    @PostMapping("/activate")
    public ConsumptionPlanService.PlanResponse activate(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.activate(id, jwt);
    }
}
