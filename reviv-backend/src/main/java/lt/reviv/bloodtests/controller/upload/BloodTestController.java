package lt.reviv.bloodtests.controller.upload;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.dto.upload.BloodTestUploadResponse;
import lt.reviv.bloodtests.service.upload.BloodTestService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tyrimai")
public class BloodTestController {

    private final BloodTestService bloodTestService;

    public BloodTestController(BloodTestService bloodTestService) {
        this.bloodTestService = bloodTestService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BloodTestUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(
                    value = "allowDuplicate",
                    defaultValue = "false"
            ) boolean allowDuplicate,
            @AuthenticationPrincipal Jwt jwt
    ) {
        BloodTest bloodTest = bloodTestService.upload(
                file,
                jwt,
                allowDuplicate
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BloodTestUploadResponse.from(bloodTest));
    }
}
