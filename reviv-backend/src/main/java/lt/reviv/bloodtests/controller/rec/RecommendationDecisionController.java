package lt.reviv.bloodtests.controller.rec;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.service.rec.RecommendationDecisionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tyrimai/{id}/recommendations")
public class RecommendationDecisionController {

    public record DecisionRequest(
            @NotNull SupplementRecommendation.Decision decision
    ) {}

    private final RecommendationDecisionService service;

    public RecommendationDecisionController(RecommendationDecisionService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public RecommendationDecisionService.LatestResult latest(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.latest(id, jwt);
    }

    @PatchMapping("/{recommendationId}/decision")
    public RecommendationDecisionService.ItemResult decide(
            @PathVariable Long id,
            @PathVariable Long recommendationId,
            @Valid @RequestBody DecisionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.decide(id, recommendationId, request.decision(), jwt);
    }
}
