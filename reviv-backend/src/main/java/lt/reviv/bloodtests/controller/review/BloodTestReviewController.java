package lt.reviv.bloodtests.controller.review;

import lt.reviv.bloodtests.dto.review.BloodTestDetailResponse;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lt.reviv.bloodtests.dto.review.MarkExtractionIssueRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;


@RestController
@RequestMapping("/api/tyrimai")
public class BloodTestReviewController {

    private final BloodTestReviewService reviewService;

    public BloodTestReviewController(
            BloodTestReviewService reviewService
    ) {
        this.reviewService = reviewService;
    }

    @GetMapping("/{id}")
    public BloodTestDetailResponse getBloodTest(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reviewService.getDetails(id, jwt);
    }


    @PostMapping("/{id}/issues")
    public BloodTestDetailResponse markIssue(
            @PathVariable Long id,
            @Valid @RequestBody MarkExtractionIssueRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reviewService.markIssue(id, request, jwt);
    }


    @DeleteMapping("/{id}/issues/{issueId}")
    public BloodTestDetailResponse unmarkIssue(
            @PathVariable Long id,
            @PathVariable Long issueId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reviewService.unmarkIssue(id, issueId, jwt);
    }

    @PostMapping("/{id}/reextract")
    public BloodTestDetailResponse reextract(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reviewService.requestCorrection(id, jwt);
    }


    @PostMapping("/{id}/confirm")
    public BloodTestDetailResponse confirm(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reviewService.confirm(id, jwt);
    }

    @GetMapping(
            value = "/{id}/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> getOriginalPdf(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_PDF)
                .body(reviewService.getOriginalPdf(id, jwt));
    }
}
