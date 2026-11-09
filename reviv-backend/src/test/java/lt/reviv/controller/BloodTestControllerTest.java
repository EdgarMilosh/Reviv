package lt.reviv.controller;

import lt.reviv.bloodtests.controller.BloodTestController;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.dto.BloodTestUploadResponse;
import lt.reviv.bloodtests.service.BloodTestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-3: DP2 - Tyrimų kontrolerio komponentas. */
@ExtendWith(MockitoExtension.class)
class BloodTestControllerTest {

    @Mock
    private BloodTestService service;

    @InjectMocks
    private BloodTestController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("google-123")
                .build();
    }

    private static BloodTest bloodTest() {
        BloodTest test = new BloodTest(42L, "tyrimas.pdf", 12L,
                "a".repeat(64), new byte[] { 1, 2, 3 });
        ReflectionTestUtils.setField(test, "id", 123L);
        return test;
    }

    @Test
    void TA15_returnsCreatedAndOnlySafeMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "tyrimas.pdf",
                "application/pdf", new byte[] { 1, 2, 3 });
        Jwt jwt = jwt();
        BloodTest bloodTest = bloodTest();
        when(service.upload(file, jwt, false)).thenReturn(bloodTest);

        ResponseEntity<BloodTestUploadResponse> response = controller.upload(file, false, jwt);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(123L, response.getBody().id().longValue());
        assertEquals("tyrimas.pdf", response.getBody().fileName());
        assertEquals(12L, response.getBody().fileSize().longValue());
        assertEquals("UPLOADED", response.getBody().status());
        assertNotNull(response.getBody().uploadedAt());
        verify(service).upload(file, jwt, false);
        // Response DTO has no pdfData or userId field.
        assertEquals(5, BloodTestUploadResponse.class.getRecordComponents().length);
    }

    @Test
    void TA26_forwardsDuplicateConfirmationToService() {
        MockMultipartFile file = new MockMultipartFile("file", "tyrimas.pdf",
                "application/pdf", new byte[] { 1, 2, 3 });
        Jwt jwt = jwt();
        when(service.upload(file, jwt, true)).thenReturn(bloodTest());

        ResponseEntity<BloodTestUploadResponse> response = controller.upload(file, true, jwt);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(service).upload(file, jwt, true);
    }
}