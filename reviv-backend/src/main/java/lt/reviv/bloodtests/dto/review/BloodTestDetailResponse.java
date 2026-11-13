package lt.reviv.bloodtests.dto.review;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.ExtractionIssue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
            String referenceInterval
    ) {
        public static Indicator from(BloodTestIndicator indicator) {
            return new Indicator(
                    indicator.getId(),
                    indicator.getName(),
                    indicator.getRawValue(),
                    indicator.getUnit(),
                    indicator.getReferenceInterval()
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

    public static BloodTestDetailResponse from(
            BloodTest test,
            List<BloodTestIndicator> indicators,
            List<ExtractionIssue> issues
    ) {
        return new BloodTestDetailResponse(
                test.getId(),
                test.getFileName(),
                test.getStatus(),
                test.getUploadedAt(),
                test.getLaboratory(),
                test.getTestDate(),
                test.getExtractionError(),
                indicators.stream().map(Indicator::from).toList(),
                issues.stream().map(Issue::from).toList()
        );
    }
}
