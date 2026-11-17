package lt.reviv.bloodtests.service.summary;

import lt.reviv.bloodtests.service.IndicatorStatusCalculator;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the doctor summary PDF shows. It is plain data, so the layout code ({@link SummaryPdfRenderer})
 * does not touch the database and can be tested on its own.
 */
public record SummaryReport(
        LocalDate generatedOn,
        String periodLabel,
        Patient patient,
        boolean includeTests,
        boolean includeAnalyses,
        boolean includePlan,
        boolean includeHistory,
        List<TestSection> tests,
        List<AnalysisNote> analyses,
        List<PlanLine> plan
) {

    public SummaryReport {
        tests = tests == null ? List.of() : List.copyOf(tests);
        analyses = analyses == null ? List.of() : List.copyOf(analyses);
        plan = plan == null ? List.of() : List.copyOf(plan);
    }

    /** Patient data; every value is already formatted for display ("nenurodyta" when missing). */
    public record Patient(String name, String email, String age, String height, String weight,
                          String allergies, String habits) {
    }

    public record IndicatorLine(String name, String value, String unit, String reference,
                                IndicatorStatusCalculator.Status status) {
    }

    public record TestSection(String fileName, String laboratory, LocalDate date, List<IndicatorLine> indicators) {

        public TestSection {
            indicators = indicators == null ? List.of() : List.copyOf(indicators);
        }

        /** Indicators that are below or above their reference interval. */
        public long outOfRange() {
            return indicators.stream()
                    .filter(i -> i.status() == IndicatorStatusCalculator.Status.BELOW_RANGE
                            || i.status() == IndicatorStatusCalculator.Status.ABOVE_RANGE)
                    .count();
        }
    }

    /** An AI explanation of one indicator (always shown with an "AI" badge). */
    public record AnalysisNote(LocalDate testDate, String indicatorName, String meaning) {
    }

    /** One supplement of the plan with its dose history; {@code adherencePercent} is null when nothing was due yet. */
    public record PlanLine(String supplement, String dose, String cycle,
                           long taken, long skipped, long unmarked, Integer adherencePercent) {
    }

    public long totalOutOfRange() {
        return tests.stream().mapToLong(TestSection::outOfRange).sum();
    }

    /** Overall adherence over all plan lines, or null when no dose was due yet. */
    public Integer overallAdherence() {
        long taken = plan.stream().mapToLong(PlanLine::taken).sum();
        long planned = plan.stream().mapToLong(line -> line.taken() + line.skipped() + line.unmarked()).sum();
        return planned <= 0 ? null : (int) Math.round(taken * 100.0 / planned);
    }
}
