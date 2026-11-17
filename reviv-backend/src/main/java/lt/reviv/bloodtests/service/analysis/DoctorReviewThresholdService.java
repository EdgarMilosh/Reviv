package lt.reviv.bloodtests.service.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Demonstracinis informacinis signalas, o NE klinikinė kritinė riba.
 * Kliniškai kritines reikšmes toliau vertina CriticalIndicatorService pagal
 * konkretaus rodiklio ir matavimo vieneto taisykles.
 */
@Component
public class DoctorReviewThresholdService {

    private final BigDecimal thresholdPercent;

    public DoctorReviewThresholdService(
            @Value("${reviv.analysis.doctor-review-threshold-percent:35}")
            BigDecimal thresholdPercent
    ) {
        if (thresholdPercent == null || thresholdPercent.signum() < 0) {
            throw new IllegalArgumentException("Nuokrypio procentinė riba negali būti neigiama.");
        }
        this.thresholdPercent = thresholdPercent;
    }

    public boolean shouldRecommendDoctorReview(
            IndicatorDeviationCalculator.Status status,
            BigDecimal deviationPercent
    ) {
        return (status == IndicatorDeviationCalculator.Status.BELOW
                || status == IndicatorDeviationCalculator.Status.ABOVE)
                && deviationPercent != null
                && deviationPercent.compareTo(thresholdPercent) > 0;
    }
}
