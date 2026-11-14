package lt.reviv.service;

import lt.reviv.bloodtests.ai.OpenAiProperties;
import lt.reviv.bloodtests.ai.extraction.ExtractedReport;
import lt.reviv.bloodtests.ai.extraction.PdfExtractionClient;
import lt.reviv.bloodtests.ai.extraction.OpenAiPdfExtractionClient;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.ExtractionIssue;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.ExtractionIssueRepository;
import lt.reviv.bloodtests.service.extraction.BloodTestCorrectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloodTestCorrectionServiceTest {
    @Mock BloodTestRepository tests;
    @Mock BloodTestIndicatorRepository indicators;
    @Mock ExtractionIssueRepository issues;
    @Mock PdfExtractionClient client;
    @Mock PlatformTransactionManager txManager;

    BloodTestCorrectionService service;
    BloodTest test;
    BloodTestIndicator hgb;
    ExtractionIssue valueIssue;

    private static OpenAiProperties settings(boolean enabled, int attempts) {
        return new OpenAiProperties(enabled, "fake", "gpt-5-nano", 5, 3_000_000, attempts, 4000);
    }
    private BloodTestCorrectionService create(OpenAiProperties options) {
        return new BloodTestCorrectionService(tests, indicators, issues, client, options, txManager);
    }
    private static ExtractedReport.Correction corrected(String value) {
        return new ExtractedReport.Correction(16L, "NOT_THE_ORIGINAL_NAME", value,
                "WRONG_UNIT", "WRONG_REFERENCE");
    }
    private ExtractionIssue issue(String field, long id) {
        ExtractionIssue issue = new ExtractionIssue(6L, 16L, field, null);
        ReflectionTestUtils.setField(issue, "id", id);
        return issue;
    }
    @BeforeEach
    void init() {
        test = new BloodTest(7L, "blood.pdf", 8L, "sha", "%PDF-123".getBytes());
        ReflectionTestUtils.setField(test, "id", 6L);
        test.startProcessing();
        test.finishExtraction("lab", null);
        test.queueCorrection();
        hgb = new BloodTestIndicator(6L, "HGB", "104", "g/L", "117-145");
        ReflectionTestUtils.setField(hgb, "id", 16L);
        valueIssue = issue("VALUE", 1L);
        when(tests.findById(6L)).thenReturn(Optional.of(test));
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of(valueIssue));
        when(indicators.findByIdAndBloodTestId(16L, 6L)).thenReturn(Optional.of(hgb));
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        service = create(settings(true, 3));
    }

    @Test void correctsOnlyFlaggedFieldAndResolvesIssue() {
        when(client.correct(any(), anyList())).thenReturn(List.of(corrected("119")));
        service.processCorrections(6L);
        assertEquals("119", hgb.getRawValue());
        assertEquals("HGB", hgb.getName());
        assertEquals("g/L", hgb.getUnit());
        assertEquals("117-145", hgb.getReferenceInterval());
        assertTrue(valueIssue.isResolved());
        assertEquals("NEEDS_REVIEW", test.getStatus());
        assertEquals(2, test.getExtractionAttempts());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExtractedReport.CorrectionTarget>> targets = ArgumentCaptor.forClass(List.class);
        verify(client).correct(any(), targets.capture());
        assertEquals(1, targets.getValue().size());
        assertEquals("VALUE", targets.getValue().getFirst().fields());
        assertEquals(16L, targets.getValue().getFirst().id());
    }
    @Test void multipleFieldsForSameIndicatorAreGroupedAndUpdated() {
        ExtractionIssue unitIssue = issue("UNIT", 2L);
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of(valueIssue, unitIssue));
        when(client.correct(any(), anyList())).thenReturn(List.of(corrected("125")));
        service.processCorrections(6L);
        assertEquals("125", hgb.getRawValue());
        assertEquals("WRONG_UNIT", hgb.getUnit());
        assertEquals("HGB", hgb.getName());
        assertEquals("117-145", hgb.getReferenceInterval());
        assertTrue(valueIssue.isResolved());
        assertTrue(unitIssue.isResolved());
        verify(client).correct(any(), argThat(ts -> ts.size() == 1 &&
                ts.getFirst().fields().contains("VALUE") && ts.getFirst().fields().contains("UNIT")));
    }
    @Test void canCorrectNameAndReferenceWithoutChangingValue() {
        ExtractionIssue name = issue("NAME", 3L);
        ExtractionIssue ref = issue("REFERENCE", 4L);
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of(name, ref));
        when(client.correct(any(), anyList())).thenReturn(List.of(
                new ExtractedReport.Correction(16L, "Hemoglobin", "999", "wrong", "120-150")));
        service.processCorrections(6L);
        assertEquals("Hemoglobin", hgb.getName());
        assertEquals("120-150", hgb.getReferenceInterval());
        assertEquals("104", hgb.getRawValue());
        assertEquals("g/L", hgb.getUnit());
    }
    @Test void disabledDoesNotCallDatabaseOrAi() {
        create(settings(false, 3)).processCorrections(6L);
        verifyNoInteractions(client);
        assertEquals("RETRY_QUEUED", test.getStatus());
    }
    @Test void ignoresOtherStatusesWithoutAi() {
        test.finishExtraction("lab", null);
        service.processCorrections(6L);
        verifyNoInteractions(client);
        assertEquals("NEEDS_REVIEW", test.getStatus());
    }
    @Test void reachingAttemptLimitFailsBeforeAi() {
        create(settings(true, 1)).processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void noIssuesMarksErrorWithoutAi() {
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of());
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void missingIndicatorMarksError() {
        when(indicators.findByIdAndBloodTestId(16L, 6L)).thenReturn(Optional.empty());
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void apiFailureMarksErrorWithoutResolvingIssues() {
        when(client.correct(any(), anyList())).thenThrow(new IllegalStateException("failed"));
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        assertFalse(valueIssue.isResolved());
    }
    @Test void nullResponseMarksError() {
        when(client.correct(any(), anyList())).thenReturn(null);
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        assertFalse(valueIssue.isResolved());
    }
    @Test void missingCorrectedTargetMarksError() {
        when(client.correct(any(), anyList())).thenReturn(List.of());
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        assertEquals("104", hgb.getRawValue());
    }
    @Test void unexpectedTargetIdMarksError() {
        when(client.correct(any(), anyList())).thenReturn(List.of(
                new ExtractedReport.Correction(999L, "HGB", "120", "g/L", "117-145")));
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        assertEquals("104", hgb.getRawValue());
    }
    @Test void duplicateTargetIdMarksError() {
        when(client.correct(any(), anyList())).thenReturn(List.of(corrected("120"), corrected("121")));
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
    }
    @Test void emptyRequiredValueIsRejected() {
        when(client.correct(any(), anyList())).thenReturn(List.of(corrected(" ")));
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
        assertEquals("104", hgb.getRawValue());
    }
    @Test void overlongValueIsRejected() {
        when(client.correct(any(), anyList())).thenReturn(List.of(corrected("A".repeat(129))));
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
    }
    @Test void nullUnitIsAllowedForFlaggedUnit() {
        ExtractionIssue unit = issue("UNIT", 2L);
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of(unit));
        when(client.correct(any(), anyList())).thenReturn(List.of(
                new ExtractedReport.Correction(16L, "HGB", "104", null, "117-145")));
        service.processCorrections(6L);
        assertEquals("NEEDS_REVIEW", test.getStatus());
        assertNull(hgb.getUnit());
        assertTrue(unit.isResolved());
    }
    @Test void emptyNameIsRejectedWhenNameFlagged() {
        ExtractionIssue name = issue("NAME", 2L);
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of(name));
        when(client.correct(any(), anyList())).thenReturn(List.of(
                new ExtractedReport.Correction(16L, "", "104", "g/L", "117-145")));
        service.processCorrections(6L);
        assertEquals("ERROR", test.getStatus());
    }
    @Test void stateChangeDuringAiCallStopsWrite() {
        when(client.correct(any(), anyList())).thenAnswer(invocation -> {
            test.finishExtraction("concurrent change", null);
            return List.of(corrected("119"));
        });
        service.processCorrections(6L);
        assertEquals("NEEDS_REVIEW", test.getStatus());
        assertEquals("104", hgb.getRawValue());
        assertFalse(valueIssue.isResolved());
    }
    @Test void missingReportRecordDoesNotCallAi() {
        when(tests.findById(6L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> service.processCorrections(6L));
        verifyNoInteractions(client);
    }
}
