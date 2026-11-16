package lt.reviv.bloodtests.dto.trend;

import java.time.LocalDate;

public record IndicatorHistoryPoint(
        Long testId,
        LocalDate date,
        String value,
        String unit,
        String referenceInterval
) {
}
