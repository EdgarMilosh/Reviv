package lt.reviv.bloodtests.controller.analysis;

import lt.reviv.bloodtests.dto.analysis.AnalysisResultResponse;
import lt.reviv.bloodtests.service.analysis.BloodTestAnalysisReadService;
import lt.reviv.bloodtests.service.analysis.BloodTestAnalysisRequestService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tyrimai")
public class BloodTestAnalysisController {

    private final BloodTestAnalysisRequestService requestService;
    private final BloodTestAnalysisReadService readService;

    public BloodTestAnalysisController(
            BloodTestAnalysisRequestService requestService,
            BloodTestAnalysisReadService readService
    ) {
        this.requestService = requestService;
        this.readService = readService;
    }

    @PostMapping("/{id}/analysis")
    public ResponseEntity<
            BloodTestAnalysisRequestService.RequestResult
            > requestAnalysis(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = requestService.requestAnalysis(id, jwt);

        return ResponseEntity
                .status(
                        result.newlyRequested()
                                ? HttpStatus.ACCEPTED
                                : HttpStatus.OK
                )
                .body(result);
    }

    @GetMapping("/{id}/analysis")
    public AnalysisResultResponse getAnalysis(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return readService.getAnalysis(id, jwt);
    }
}
