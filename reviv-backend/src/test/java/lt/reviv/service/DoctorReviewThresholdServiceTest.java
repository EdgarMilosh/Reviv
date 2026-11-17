package lt.reviv.service;

import lt.reviv.bloodtests.service.analysis.DoctorReviewThresholdService;
import lt.reviv.bloodtests.service.analysis.IndicatorDeviationCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DoctorReviewThresholdServiceTest {

    private final DoctorReviewThresholdService service =
            new DoctorReviewThresholdService(new BigDecimal("35"));

    @Test
    void doesNotFlagDeviationBelowOrExactlyAtThreshold() {
        assertFalse(service.shouldRecommendDoctorReview(
                IndicatorDeviationCalculator.Status.BELOW, new BigDecimal("34.9")));
        assertFalse(service.shouldRecommendDoctorReview(
                IndicatorDeviationCalculator.Status.ABOVE, new BigDecimal("35.0")));
    }

    @Test
    void flagsBothLowAndHighDeviationsAboveThreshold() {
        assertTrue(service.shouldRecommendDoctorReview(
                IndicatorDeviationCalculator.Status.BELOW, new BigDecimal("35.1")));
        assertTrue(service.shouldRecommendDoctorReview(
                IndicatorDeviationCalculator.Status.ABOVE, new BigDecimal("49.6")));
    }

    @Test
    void doesNotFlagMissingOrUnknownData() {
        assertFalse(service.shouldRecommendDoctorReview(
                IndicatorDeviationCalculator.Status.UNKNOWN, null));
        assertFalse(service.shouldRecommendDoctorReview(
                IndicatorDeviationCalculator.Status.BELOW, null));
        assertFalse(service.shouldRecommendDoctorReview(
                IndicatorDeviationCalculator.Status.NORMAL, new BigDecimal("50")));
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> new DoctorReviewThresholdService(new BigDecimal("-1")));
    }
}
