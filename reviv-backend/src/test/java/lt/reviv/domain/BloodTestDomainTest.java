package lt.reviv.domain;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.ExtractionIssue;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BloodTestDomainTest {

    private BloodTest newTest() {
        return new BloodTest(7L, "tyrimas.pdf", 3L, "a".repeat(64),
                new byte[]{1, 2, 3});
    }

    @Test
    void createsUploadedTestAndKeepsPdfBytes() {
        BloodTest test = newTest();
        assertEquals(7L, test.getUserId().longValue());
        assertEquals("tyrimas.pdf", test.getFileName());
        assertEquals(3L, test.getFileSize().longValue());
        assertEquals("UPLOADED", test.getStatus());
        assertArrayEquals(new byte[]{1, 2, 3}, test.getPdfData());
        assertNotNull(test.getUploadedAt());
        assertEquals(0, test.getExtractionAttempts());
    }

    @Test
    void processingIncrementsAttemptAndFinishingReturnsToReview() {
        BloodTest test = newTest();
        test.startProcessing();
        assertEquals("PROCESSING", test.getStatus());
        assertEquals(1, test.getExtractionAttempts());

        LocalDate date = LocalDate.of(2026, 11, 12);
        test.finishExtraction("DEMO LAB", date);
        assertEquals("NEEDS_REVIEW", test.getStatus());
        assertEquals("DEMO LAB", test.getLaboratory());
        assertEquals(date, test.getTestDate());
        assertNull(test.getExtractionError());
    }

    @Test
    void failureSetsErrorAndRetryResetsStatus() {
        BloodTest test = newTest();
        test.startProcessing();
        test.failExtraction();
        assertEquals("ERROR", test.getStatus());
        assertNotNull(test.getExtractionError());
        assertFalse(test.getExtractionError().isBlank());
        test.resetForRetry();
        assertEquals("UPLOADED", test.getStatus());
    }

    @Test
    void correctionCanBeQueuedAndStarted() {
        BloodTest test = newTest();
        test.startProcessing();
        test.finishExtraction(null, null);
        test.queueCorrection();
        assertEquals("RETRY_QUEUED", test.getStatus());
        test.startProcessing();
        assertEquals("PROCESSING", test.getStatus());
        assertEquals(2, test.getExtractionAttempts());
    }

    @Test
    void reviewedTestCanBeConfirmed() {
        BloodTest test = newTest();
        test.startProcessing();
        test.finishExtraction("DEMO LAB", LocalDate.of(2026, 11, 12));
        test.confirm();
        assertEquals("CONFIRMED", test.getStatus());
    }

    @Test
    void indicatorUpdatesOnlyRequestedField() {
        BloodTestIndicator original = new BloodTestIndicator(
                1L, "WBC", "8,93", "10^9/L", "4,0-9,8");
        BloodTestIndicator corrected = new BloodTestIndicator(
                1L, "Leukocitai", "9,10", "x10^9/L", "4,1-10,0");

        original.replaceField("VALUE", corrected);
        assertEquals("WBC", original.getName());
        assertEquals("9,10", original.getRawValue());
        assertEquals("10^9/L", original.getUnit());
        assertEquals("4,0-9,8", original.getReferenceInterval());

        original.replaceField("UNIT", corrected);
        assertEquals("x10^9/L", original.getUnit());
        assertEquals("WBC", original.getName());
        assertEquals("4,0-9,8", original.getReferenceInterval());

        original.replaceField("NAME", corrected);
        assertEquals("Leukocitai", original.getName());
        assertEquals("9,10", original.getRawValue());

        original.replaceField("REFERENCE", corrected);
        assertEquals("4,1-10,0", original.getReferenceInterval());
    }

    @Test
    void unknownIndicatorFieldIsRejected() {
        BloodTestIndicator original = new BloodTestIndicator(
                1L, "WBC", "8,93", "%", "4-10");
        BloodTestIndicator corrected = new BloodTestIndicator(
                1L, "WBC", "9", "%", "4-10");
        assertThrows(IllegalArgumentException.class,
                () -> original.replaceField("NOT_A_FIELD", corrected));
        assertEquals("8,93", original.getRawValue());
    }

    @Test
    void issueIsInitiallyUnresolvedAndCanBeResolved() {
        ExtractionIssue issue = new ExtractionIssue(1L, 16L, "VALUE", "Bad value");
        assertEquals(1L, issue.getBloodTestId().longValue());
        assertEquals(16L, issue.getIndicatorId().longValue());
        assertFalse(issue.isResolved());
        assertNotNull(issue.getCreatedAt());
        issue.resolve();
        assertTrue(issue.isResolved());
    }
}
