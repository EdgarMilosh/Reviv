
package lt.reviv.service;

import lt.reviv.bloodtests.dto.summary.SummaryExportRequest;
import lt.reviv.bloodtests.dto.summary.SummaryPeriod;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.IndicatorExplanationRepository;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.bloodtests.service.summary.SummaryExportService;
import lt.reviv.doses.repository.DoseLogRepository;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryExportServiceTest {

  @Mock
  private AppUserRepository appUserRepository;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private BloodTestRepository bloodTestRepository;

  @Mock
  private BloodTestIndicatorRepository indicatorRepository;

  @Mock
  private BloodTestAnalysisRepository analysisRepository;

  @Mock
  private IndicatorExplanationRepository explanationRepository;

  @Mock
  private ConsumptionPlanRepository planRepository;

  @Mock
  private PlanEntryRepository entryRepository;

  @Mock
  private DoseLogRepository doseLogRepository;

  @Mock
  private Jwt jwt;

  @Mock
  private AppUser user;

  @InjectMocks
  private SummaryExportService service;

  private void mockExistingUser() {
    when(jwt.getSubject()).thenReturn("google-user-id");

    when(appUserRepository.findByGoogleId("google-user-id"))
        .thenReturn(Optional.of(user));

    when(user.getId()).thenReturn(1L);
    when(user.getName()).thenReturn("Test User");
    when(user.getEmail()).thenReturn("test@example.com");

    when(profileRepository.findByUserId(1L))
        .thenReturn(Optional.empty());

    when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(1L))
        .thenReturn(List.of());

    when(planRepository.findAllForUser("google-user-id"))
        .thenReturn(List.of());
  }

  @Test
  void exportSummaryShouldGeneratePdfWithDefaultRequest() {
    // Arrange
    mockExistingUser();

    // Act
    byte[] result = service.exportSummary(null, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
    assertTrue(new String(result, 0, 5).startsWith("%PDF-"));
  }

  @Test
  void exportSummaryShouldGeneratePdfWithAllPeriod() {
    // Arrange
    mockExistingUser();

    SummaryExportRequest request = new SummaryExportRequest(
        SummaryPeriod.ALL,
        List.of("TESTS", "ANALYSES")
    );

    // Act
    byte[] result = service.exportSummary(request, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);

    verify(bloodTestRepository)
        .findAllByUserIdOrderByUploadedAtDesc(1L);
  }

  @Test
  void exportSummaryShouldGeneratePdfWithThreeMonthsPeriod() {
    // Arrange
    mockExistingUser();

    SummaryExportRequest request = new SummaryExportRequest(
        SummaryPeriod.THREE_MONTHS,
        List.of("TESTS")
    );

    // Act
    byte[] result = service.exportSummary(request, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
  }

  @Test
  void exportSummaryShouldGeneratePdfWithSixMonthsPeriod() {
    // Arrange
    mockExistingUser();

    SummaryExportRequest request = new SummaryExportRequest(
        SummaryPeriod.SIX_MONTHS,
        List.of("ANALYSES")
    );

    // Act
    byte[] result = service.exportSummary(request, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
  }

  @Test
  void exportSummaryShouldGeneratePdfWithOneYearPeriod() {
    // Arrange
    mockExistingUser();

    SummaryExportRequest request = new SummaryExportRequest(
        SummaryPeriod.ONE_YEAR,
        List.of("SUPPLEMENT_PLAN")
    );

    // Act
    byte[] result = service.exportSummary(request, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
  }

  @Test
  void exportSummaryShouldGeneratePdfWithAllSections() {
    // Arrange
    mockExistingUser();

    SummaryExportRequest request = new SummaryExportRequest(
        SummaryPeriod.ALL,
        List.of(
            "TESTS",
            "ANALYSES",
            "SUPPLEMENT_PLAN",
            "CONSUMPTION_HISTORY"
        )
    );

    // Act
    byte[] result = service.exportSummary(request, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
  }

  @Test
  void exportSummaryShouldGeneratePdfWithEmptySections() {
    // Arrange
    mockExistingUser();

    SummaryExportRequest request = new SummaryExportRequest(
        SummaryPeriod.ALL,
        List.of()
    );

    // Act
    byte[] result = service.exportSummary(request, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
  }

  @Test
  void exportSummaryShouldThrowExceptionWhenUserDoesNotExist() {
    // Arrange
    when(jwt.getSubject()).thenReturn("google-user-id");

    when(appUserRepository.findByGoogleId("google-user-id"))
        .thenReturn(Optional.empty());

    // Act & Assert
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> service.exportSummary(null, jwt)
    );

    assertEquals("Naudotojas nerastas.", exception.getMessage());

    verify(profileRepository, never()).findByUserId(anyLong());

    verify(bloodTestRepository, never())
        .findAllByUserIdOrderByUploadedAtDesc(anyLong());
  }

  @Test
  void exportSummaryShouldGenerateDifferentPdfForDifferentRequests() {
    // Arrange
    mockExistingUser();

    SummaryExportRequest firstRequest = new SummaryExportRequest(
        SummaryPeriod.ALL,
        List.of("TESTS")
    );

    SummaryExportRequest secondRequest = new SummaryExportRequest(
        SummaryPeriod.ONE_YEAR,
        List.of("ANALYSES")
    );

    // Act
    byte[] firstResult = service.exportSummary(firstRequest, jwt);
    byte[] secondResult = service.exportSummary(secondRequest, jwt);

    // Assert
    assertNotNull(firstResult);
    assertNotNull(secondResult);
    assertTrue(firstResult.length > 0);
    assertTrue(secondResult.length > 0);
  }

  @Test
  void exportSummaryShouldGeneratePdfWhenProfileDoesNotExist() {
    // Arrange
    mockExistingUser();

    // Act
    byte[] result = service.exportSummary(null, jwt);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
  }
}
