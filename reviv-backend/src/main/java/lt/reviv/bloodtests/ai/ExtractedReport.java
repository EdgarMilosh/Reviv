package lt.reviv.bloodtests.ai;

import java.util.List;

public record ExtractedReport(
        String laboratory,
        String testDate,
        List<Indicator> indicators
) {

    public record Indicator(
            String name,
            String rawValue,
            String unit,
            String referenceInterval
    ) {}

    public record Correction(
            Long targetId,
            String name,
            String rawValue,
            String unit,
            String referenceInterval
    ) {}

    public record CorrectionTarget(
            Long id,
            String name,
            String rawValue,
            String unit,
            String referenceInterval,
            String fields,
            String notes
    ) {}
}
