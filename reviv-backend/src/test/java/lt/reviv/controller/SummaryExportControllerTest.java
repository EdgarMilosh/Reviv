package lt.reviv.controller;

import lt.reviv.bloodtests.dto.summary.SummaryExportRequest;
import lt.reviv.bloodtests.dto.summary.SummaryPeriod;
import lt.reviv.bloodtests.service.summary.SummaryExportController;
import lt.reviv.bloodtests.service.summary.SummaryExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryExportControllerTest {

  @Mock
  private SummaryExportService service;

  @Mock
  private Jwt jwt;

  @InjectMocks
  private SummaryExportController controller;

  @Test
  void exportSummaryGetShouldReturnPdf() {
    // Arrange
    byte[] pdf = "%PDF-test".getBytes();

    when(service.exportSummary(any(SummaryExportRequest.class), eq(jwt)))
        .thenReturn(pdf);

    // Act
    ResponseEntity<byte[]> response = controller.exportSummaryGet(jwt);

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    assertArrayEquals(pdf, response.getBody());

    verify(service).exportSummary(
        argThat(request ->
            request.period() == SummaryPeriod.ALL
                && request.sections() != null
        ),
        eq(jwt)
    );
  }

  @Test
  void exportSummaryShouldReturnPdfForPostRequest() {
    // Arrange
    SummaryExportRequest request = new SummaryExportRequest(
        SummaryPeriod.THREE_MONTHS,
        List.of("TESTS", "ANALYSES")
    );

    byte[] pdf = "%PDF-test".getBytes();

    when(service.exportSummary(request, jwt)).thenReturn(pdf);

    // Act
    ResponseEntity<byte[]> response = controller.exportSummary(request, jwt);

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    assertArrayEquals(pdf, response.getBody());

    verify(service).exportSummary(request, jwt);
  }

  @Test
  void exportSummaryShouldReturnAttachmentFilename() {
    // Arrange
    when(service.exportSummary(any(), eq(jwt)))
        .thenReturn("%PDF-test".getBytes());

    // Act
    ResponseEntity<byte[]> response = controller.exportSummary(
        new SummaryExportRequest(null, null),
        jwt
    );

    // Assert
    String expectedFilename = "Reviv_suvestine_"
        + LocalDate.now()
        + ".pdf";

    assertEquals(
        "attachment; filename=\"" + expectedFilename + "\"",
        response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
    );
  }

  @Test
  void exportSummaryShouldSetNoStoreCacheControl() {
    // Arrange
    when(service.exportSummary(any(), eq(jwt)))
        .thenReturn("%PDF-test".getBytes());

    // Act
    ResponseEntity<byte[]> response = controller.exportSummary(
        new SummaryExportRequest(null, null),
        jwt
    );

    // Assert
    assertEquals(
        "no-store",
        response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)
    );
  }

  @Test
  void exportSummaryShouldReturnServicePdfWithoutModification() {
    // Arrange
    byte[] expectedPdf = {1, 2, 3, 4, 5};

    when(service.exportSummary(any(), eq(jwt)))
        .thenReturn(expectedPdf);

    // Act
    ResponseEntity<byte[]> response = controller.exportSummary(
        new SummaryExportRequest(
            SummaryPeriod.ONE_YEAR,
            List.of("TESTS")
        ),
        jwt
    );

    // Assert
    assertNotNull(response.getBody());
    assertArrayEquals(expectedPdf, response.getBody());
  }

  @Test
  void exportSummaryShouldPropagateServiceException() {
    // Arrange
    when(service.exportSummary(any(), eq(jwt)))
        .thenThrow(new IllegalArgumentException("Naudotojas nerastas."));

    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> controller.exportSummary(
            new SummaryExportRequest(null, null),
            jwt
        )
    );

    assertEquals("Naudotojas nerastas.", exception.getMessage());
  }
}
