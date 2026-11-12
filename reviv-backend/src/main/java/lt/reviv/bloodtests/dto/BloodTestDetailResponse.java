package lt.reviv.bloodtests.dto;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.ExtractionIssue;
import lt.reviv.bloodtests.service.IndicatorStatusCalculator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Comparator;

public record BloodTestDetailResponse(
        Long id,
        String fileName,
        String status,
        OffsetDateTime uploadedAt,
        String laboratory,
        LocalDate testDate,
        String extractionError,
        List<Indicator> indicators,
        List<Issue> issues
) {

    public record Indicator(
            Long id,
            String name,
            String rawValue,
            String unit,
            String referenceInterval,
            IndicatorStatusCalculator.Status indicatorStatus
    ) {
        public static Indicator from(BloodTestIndicator indicator) {
            return new Indicator(
                    indicator.getId(),
                    indicator.getName(),
                    indicator.getRawValue(),
                    indicator.getUnit(),
                    indicator.getReferenceInterval(),
                    IndicatorStatusCalculator.calculate(
                            indicator.getRawValue(),
                            indicator.getReferenceInterval()
                    )
            );
        }
    }

    public record Issue(
            Long id,
            Long indicatorId,
            String fieldName,
            String note,
            boolean resolved
    ) {
        public static Issue from(ExtractionIssue issue) {
            return new Issue(
                    issue.getId(),
                    issue.getIndicatorId(),
                    issue.getFieldName(),
                    issue.getNote(),
                    issue.isResolved()
            );
        }
    }

    public static BloodTestDetailResponse from(BloodTest test, List<BloodTestIndicator> indicators, List<ExtractionIssue> issues)
    {
        List<Indicator> mappedIndicators = indicators.stream()
                .map(Indicator::from)
                .toList();

        if ("CONFIRMED".equals(test.getStatus())) {
            mappedIndicators = mappedIndicators.stream()
                    .sorted(Comparator.comparingInt(
                            indicator -> switch (indicator.indicatorStatus()) {
                                case BELOW_RANGE, ABOVE_RANGE -> 0;
                                case UNDETERMINED -> 1;
                                case WITHIN_RANGE -> 2;
                            }
                    ))
                    .toList();
        }


        return new BloodTestDetailResponse(
                test.getId(),
                test.getFileName(),
                test.getStatus(),
                test.getUploadedAt(),
                test.getLaboratory(),
                test.getTestDate(),
                test.getExtractionError(),
                mappedIndicators,
                issues.stream().map(Issue::from).toList()
        );
    }
}
