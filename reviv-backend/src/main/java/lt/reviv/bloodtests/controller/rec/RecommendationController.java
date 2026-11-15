package lt.reviv.bloodtests.controller.rec;

import lt.reviv.bloodtests.service.rec.RecommendationPreviewService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tyrimai")
public class RecommendationController {

    private final RecommendationPreviewService previewService;

    public RecommendationController(
            RecommendationPreviewService previewService
    ) {
        this.previewService = previewService;
    }

    @PostMapping("/{id}/recommendations/preview")
    public ResponseEntity<RecommendationPreviewService.PreviewResponse>
    generatePreview(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(previewService.generate(id, jwt));
    }
}
