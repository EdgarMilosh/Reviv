package lt.reviv.bloodtests.service.summary;

import lt.reviv.bloodtests.dto.summary.SummaryExportRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api")
public class SummaryExportController {

    private final SummaryExportService service;

    public SummaryExportController(SummaryExportService service) {
        this.service = service;
    }

    @GetMapping(value = "/suvestine", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportSummaryGet(@AuthenticationPrincipal Jwt jwt) {
        return exportSummary(new SummaryExportRequest(null, null), jwt);
    }

    @PostMapping(value = {"/suvestine", "/planai/summary"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportSummary(
            @RequestBody(required = false) SummaryExportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        byte[] pdf = service.exportSummary(request, jwt);
        String filename = "Reviv_suvestine_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
