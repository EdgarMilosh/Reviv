package lt.reviv.controller;

import lt.reviv.bloodtests.controller.BloodTestReviewController;
import lt.reviv.bloodtests.dto.BloodTestDetailResponse;
import lt.reviv.bloodtests.dto.MarkExtractionIssueRequest;
import lt.reviv.bloodtests.service.BloodTestReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodTestReviewControllerTest {

    @Mock
    private BloodTestReviewService service;

    @InjectMocks
    private BloodTestReviewController controller;

    private Jwt jwt;
    private BloodTestDetailResponse detail;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("google-test-user")
                .build();
        detail = new BloodTestDetailResponse(
                6L, "sintetinis.pdf", "NEEDS_REVIEW", null,
                "DEMO LAB", null, null, List.of(), List.of());
    }

    @Test
    void returnsTestDetails() {
        when(service.getDetails(6L, jwt)).thenReturn(detail);
        assertSame(detail, controller.getBloodTest(6L, jwt));
        verify(service).getDetails(6L, jwt);
    }

    @Test
    void registersIssue() {
        var request = new MarkExtractionIssueRequest(16L, "VALUE", null);
        when(service.markIssue(6L, request, jwt)).thenReturn(detail);
        assertSame(detail, controller.markIssue(6L, request, jwt));
        verify(service).markIssue(6L, request, jwt);
    }

    @Test
    void removesIssue() {
        when(service.unmarkIssue(6L, 3L, jwt)).thenReturn(detail);
        assertSame(detail, controller.unmarkIssue(6L, 3L, jwt));
        verify(service).unmarkIssue(6L, 3L, jwt);
    }

    @Test
    void startsReextraction() {
        when(service.requestCorrection(6L, jwt)).thenReturn(detail);
        assertSame(detail, controller.reextract(6L, jwt));
        verify(service).requestCorrection(6L, jwt);
    }

    @Test
    void confirmsTest() {
        when(service.confirm(6L, jwt)).thenReturn(detail);
        assertSame(detail, controller.confirm(6L, jwt));
        verify(service).confirm(6L, jwt);
    }

    @Test
    void returnsPdfWithCorrectContentTypeAndNoStoreHeader() {
        byte[] pdf = new byte[]{37, 80, 68, 70};
        when(service.getOriginalPdf(6L, jwt)).thenReturn(pdf);
        ResponseEntity<byte[]> response = controller.getOriginalPdf(6L, jwt);
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertArrayEquals(pdf, response.getBody());
        verify(service).getOriginalPdf(6L, jwt);
    }
}
