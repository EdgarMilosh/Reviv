package lt.reviv.bloodtests.dto.summary;

import java.util.List;

public record SummaryExportRequest(
        SummaryPeriod period,
        List<String> sections
) {
    public SummaryExportRequest {
        if (period == null) {
            period = SummaryPeriod.ALL;
        }
        if (sections == null || sections.isEmpty()) {
            sections = List.of("TESTS", "ANALYSES", "SUPPLEMENT_PLAN", "CONSUMPTION_HISTORY");
        } else {
            sections = sections.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .distinct()
                    .toList();
        }
    }
}
