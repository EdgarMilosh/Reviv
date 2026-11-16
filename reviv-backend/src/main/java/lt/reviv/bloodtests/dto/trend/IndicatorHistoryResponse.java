package lt.reviv.bloodtests.dto.trend;

import java.util.List;

public record IndicatorHistoryResponse(
        String indicatorId,
        String name,
        String unit,
        String referenceInterval,
        List<IndicatorHistoryPoint> points,
        boolean hasEnoughData
) {
}
