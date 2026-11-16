package lt.reviv.bloodtests.dto.trend;

public record IndicatorOptionResponse(
        String id,
        String name,
        String unit,
        String referenceInterval
) {
}
