package lt.reviv.bloodtests.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisResultResponse(
        Long bloodTestId,
        String status,
        String aiModel,
        int normalCount,
        List<IndicatorResult> indicators
) {

    public record IndicatorResult(
            Long indicatorId,
            String name,
            String rawValue,
            String unit,
            String referenceInterval,
            String status,
            BigDecimal deviationPercent,
            String criticalStatus,
            String meaning,
            List<String> possibleCauses,
            String practicalAdvice
    ) {}
}
