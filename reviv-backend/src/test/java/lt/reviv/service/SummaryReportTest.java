package lt.reviv.service;

import lt.reviv.bloodtests.service.IndicatorStatusCalculator.Status;
import lt.reviv.bloodtests.service.summary.SummaryReport;
import lt.reviv.bloodtests.service.summary.SummaryReport.IndicatorLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.Patient;
import lt.reviv.bloodtests.service.summary.SummaryReport.PlanLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.TestSection;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** US-14: figures the summary derives from its data. */
class SummaryReportTest {

    private static final Patient PATIENT = new Patient("A", "a@a.lt", "1 m.", "1 cm", "1 kg", "-", "-");

    private static IndicatorLine line(Status status) {
        return new IndicatorLine("R", "1", "u", "1 - 2", status);
    }

    private static SummaryReport report(List<TestSection> tests, List<PlanLine> plan) {
        return new SummaryReport(LocalDate.of(2026, 11, 17), "Visas", PATIENT, true, true, true, true,
                tests, List.of(), plan);
    }

    @Test
    void TA72_outOfRangeCountsOnlyBelowAndAboveIndicators() {
        TestSection section = new TestSection("a.pdf", "Lab", LocalDate.of(2026, 1, 1), List.of(
                line(Status.BELOW_RANGE), line(Status.ABOVE_RANGE), line(Status.WITHIN_RANGE),
                line(Status.UNDETERMINED), line(null)));

        assertEquals(2, section.outOfRange());
        assertEquals(4, report(List.of(section, section), List.of()).totalOutOfRange());
    }

    @Test
    void TA72_overallAdherenceIsTakenDividedByAllDueDoses() {
        SummaryReport report = report(List.of(), List.of(
                new PlanLine("A", "1 mg", "-", 6, 2, 2, 60),
                new PlanLine("B", "1 mg", "-", 4, 0, 6, 40)));

        assertEquals(50, report.overallAdherence());
    }

    @Test
    void TA72_overallAdherenceIsMissingWhenNothingWasDue() {
        assertNull(report(List.of(), List.of()).overallAdherence());
        assertNull(report(List.of(), List.of(new PlanLine("A", "1 mg", "-", 0, 0, 0, null))).overallAdherence());
    }

    @Test
    void TA72_reportKeepsImmutableCopiesAndToleratesNullLists() {
        List<PlanLine> plan = new ArrayList<>(List.of(new PlanLine("A", "1 mg", "-", 1, 0, 0, 100)));
        SummaryReport report = new SummaryReport(LocalDate.of(2026, 11, 17), "Visas", PATIENT,
                true, true, true, true, null, null, plan);
        plan.clear();

        assertEquals(1, report.plan().size());
        assertEquals(0, report.tests().size());
        assertEquals(0, report.analyses().size());
        assertThrows(UnsupportedOperationException.class, () -> report.plan().add(null));
    }
}
