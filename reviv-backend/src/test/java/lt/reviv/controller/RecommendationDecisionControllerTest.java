package lt.reviv.controller;

import lt.reviv.bloodtests.controller.rec.RecommendationDecisionController;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.service.rec.RecommendationDecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationDecisionControllerTest {
    @Test
    void latestDelegatesCurrentUserAndBloodTestId() {
        var service = mock(RecommendationDecisionService.class);
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("user").build();
        var expected = mock(RecommendationDecisionService.LatestResult.class);
        when(service.latest(42L, jwt)).thenReturn(expected);

        var controller = new RecommendationDecisionController(service);

        assertSame(expected, controller.latest(42L, jwt));
        verify(service).latest(42L, jwt);
    }

    @Test
    void decisionDelegatesChosenStatusAndRecommendationId() {
        var service = mock(RecommendationDecisionService.class);
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("user").build();
        var expected = mock(RecommendationDecisionService.ItemResult.class);
        when(service.decide(42L, 7L, SupplementRecommendation.Decision.REJECTED, jwt))
                .thenReturn(expected);

        var controller = new RecommendationDecisionController(service);
        var request = new RecommendationDecisionController.DecisionRequest(
                SupplementRecommendation.Decision.REJECTED);

        assertSame(expected, controller.decide(42L, 7L, request, jwt));
        verify(service).decide(42L, 7L, SupplementRecommendation.Decision.REJECTED, jwt);
    }
}
