package lt.reviv.dto;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.ExtractionIssue;
import lt.reviv.bloodtests.dto.review.BloodTestDetailResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BloodTestDetailResponseTest {

    @Test
    void mapsBloodTestIndicatorsAndIssues() {
        BloodTest test = new BloodTest(7L, "mock.pdf", 3L, "a".repeat(64),
                new byte[]{1, 2, 3});
        BloodTestIndicator indicator = new BloodTestIndicator(
                5L, "HGB", "104", "g/L", "117-145");
        ExtractionIssue issue = new ExtractionIssue(
                5L, 16L, "VALUE", "Wrong value");

        BloodTestDetailResponse response = BloodTestDetailResponse.from(
                test, List.of(indicator), List.of(issue));

        assertEquals("mock.pdf", response.fileName());
        assertEquals("UPLOADED", response.status());
        assertEquals(1, response.indicators().size());
        assertEquals("HGB", response.indicators().get(0).name());
        assertEquals("104", response.indicators().get(0).rawValue());
        assertEquals(1, response.issues().size());
        assertEquals(16L, response.issues().get(0).indicatorId().longValue());
        assertFalse(response.issues().get(0).resolved());
    }

    @Test
    void supportsEmptyIndicatorAndIssueLists() {
        BloodTest test = new BloodTest(7L, "empty.pdf", 1L, "b".repeat(64),
                new byte[]{1});
        BloodTestDetailResponse response = BloodTestDetailResponse.from(
                test, List.of(), List.of());
        assertTrue(response.indicators().isEmpty());
        assertTrue(response.issues().isEmpty());
    }
}
