package lt.reviv.service;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.IndicatorExplanation;
import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.dto.summary.SummaryExportRequest;
import lt.reviv.bloodtests.dto.summary.SummaryPeriod;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.IndicatorExplanationRepository;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.bloodtests.repository.plan.PlanListRow;
import lt.reviv.bloodtests.service.IndicatorStatusCalculator.Status;
import lt.reviv.bloodtests.service.summary.SummaryExportService;
import lt.reviv.bloodtests.service.summary.SummaryReport;
import lt.reviv.bloodtests.service.summary.SummaryReport.PlanLine;
import lt.reviv.doses.domain.DoseLog;
import lt.reviv.doses.repository.DoseLogRepository;
import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** US-14: what the doctor summary collects (patient, indicators, AI notes, plan and adherence). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SummaryExportServiceReportTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vilnius");

    private final LocalDate today = LocalDate.now(ZONE);

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private BloodTestRepository bloodTestRepository;
    @Mock
    private BloodTestIndicatorRepository indicatorRepository;
    @Mock
    private BloodTestAnalysisRepository analysisRepository;
    @Mock
    private IndicatorExplanationRepository explanationRepository;
    @Mock
    private ConsumptionPlanRepository planRepository;
    @Mock
    private PlanEntryRepository entryRepository;
    @Mock
    private DoseLogRepository doseLogRepository;
    @Mock
    private Jwt jwt;
    @Mock
    private AppUser user;

    @InjectMocks
    private SummaryExportService service;

    private void signedInUser(Profile profile) {
        when(jwt.getSubject()).thenReturn("google-1");
        when(appUserRepository.findByGoogleId("google-1")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(7L);
        when(user.getName()).thenReturn("Domantas Martišiūnas");
        when(user.getEmail()).thenReturn("domantas@example.com");
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.ofNullable(profile));
        when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of());
        when(planRepository.findAllForUser("google-1")).thenReturn(List.of());
    }

    // Mockito: build mocks into local variables BEFORE calling when(...), never inside thenReturn(...).
    private BloodTest test(long id, String file, String laboratory, LocalDate testDate, OffsetDateTime uploadedAt) {
        BloodTest test = mock(BloodTest.class);
        when(test.getId()).thenReturn(id);
        when(test.getFileName()).thenReturn(file);
        when(test.getLaboratory()).thenReturn(laboratory);
        when(test.getTestDate()).thenReturn(testDate);
        when(test.getUploadedAt()).thenReturn(uploadedAt);
        return test;
    }

    private BloodTestIndicator indicator(long id, String name, String value, String unit, String reference) {
        BloodTestIndicator indicator = mock(BloodTestIndicator.class);
        when(indicator.getId()).thenReturn(id);
        when(indicator.getName()).thenReturn(name);
        when(indicator.getRawValue()).thenReturn(value);
        when(indicator.getUnit()).thenReturn(unit);
        when(indicator.getReferenceInterval()).thenReturn(reference);
        return indicator;
    }

    private PlanEntry entry(long id, String name, String dose, String unit, LocalDate start, LocalDate end) {
        PlanEntry entry = new PlanEntry(10L, 100 + id, 5L, name, new BigDecimal(dose), unit,
                PlanEntry.DayPart.MORNING, PlanEntry.MealRelation.AFTER_MEAL, start, end, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    private static DoseLog dose(long entryId, LocalDate day, DoseLog.Status status) {
        return new DoseLog(entryId, day, status, OffsetDateTime.now());
    }

    private void activePlan(PlanEntry... entries) {
        when(planRepository.findAllForUser("google-1")).thenReturn(List.of(
                new PlanListRow(10L, 1L, ConsumptionPlan.Status.ACTIVE, null, null, "a.pdf", "Lab", today)));
        when(entryRepository.findByPlanIdOrderByIdAsc(10L)).thenReturn(List.of(entries));
    }

    private void doseLogs(long entryId, List<DoseLog> logs) {
        when(doseLogRepository.findByPlanEntryIdInAndDoseDateBetween(anyCollection(), any(LocalDate.class),
                any(LocalDate.class))).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            LocalDate from = invocation.getArgument(1);
            LocalDate to = invocation.getArgument(2);
            if (!ids.contains(entryId)) {
                return List.of();
            }
            return logs.stream().filter(l -> !l.getDoseDate().isBefore(from) && !l.getDoseDate().isAfter(to)).toList();
        });
    }

    @Test
    void TA75_reportCarriesThePatientDetailsInDisplayFormat() {
        Profile profile = mock(Profile.class);
        when(profile.getHeightCm()).thenReturn(182);
        when(profile.getBirthDate()).thenReturn(today.minusYears(29).minusDays(5));
        when(profile.getWeightKg()).thenReturn(new BigDecimal("78.50"));
        when(profile.getAllergies()).thenReturn(new String[]{"Penicilinas", "Riešutai"});
        when(profile.getHarmfulHabits()).thenReturn(new String[0]);
        signedInUser(profile);

        SummaryReport report = service.buildReport(null, jwt);

        assertEquals("Domantas Martišiūnas", report.patient().name());
        assertEquals("domantas@example.com", report.patient().email());
        assertEquals("29 m.", report.patient().age());
        assertEquals("182 cm", report.patient().height());
        assertEquals("78.5 kg", report.patient().weight());
        assertEquals("Penicilinas, Riešutai", report.patient().allergies());
        assertEquals("nenurodyta", report.patient().habits());
        assertEquals(today, report.generatedOn());
        assertEquals("Visas laikotarpis", report.periodLabel());
    }

    @Test
    void TA75_reportWithoutAProfileShowsNotSpecified() {
        signedInUser(null);

        SummaryReport report = service.buildReport(null, jwt);

        assertEquals("nenurodyta", report.patient().age());
        assertEquals("nenurodyta", report.patient().height());
        assertEquals("nenurodyta", report.patient().weight());
        assertEquals("nenurodyta", report.patient().allergies());
        assertEquals("nenurodyta", report.patient().habits());
    }

    @Test
    void TA75_futureBirthDateIsNotShownAsAge() {
        Profile profile = mock(Profile.class);
        when(profile.getBirthDate()).thenReturn(today.plusDays(3));
        signedInUser(profile);

        assertEquals("nenurodyta", service.buildReport(null, jwt).patient().age());
    }

    @Test
    void TA75_indicatorsAreClassifiedByTheirReferenceInterval() {
        signedInUser(null);
        BloodTest test = test(1L, "Antakpol.pdf", "Antakalnio poliklinika", today.minusDays(20), OffsetDateTime.now());
        when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(test));
        List<BloodTestIndicator> indicators = List.of(
                indicator(11L, "Vitaminas D", "18", "ng/ml", "30 - 100"),
                indicator(12L, "Hemoglobinas", "135", "g/l", "120 - 160"),
                indicator(13L, "Trombokritas", "0.35", "%", "0.1 - 0.3"),
                indicator(14L, "Be intervalo", "7", "x", null));
        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(1L)).thenReturn(indicators);

        SummaryReport report = service.buildReport(null, jwt);

        var lines = report.tests().get(0).indicators();
        assertEquals(Status.BELOW_RANGE, lines.get(0).status());
        assertEquals(Status.WITHIN_RANGE, lines.get(1).status());
        assertEquals(Status.ABOVE_RANGE, lines.get(2).status());
        assertEquals(Status.UNDETERMINED, lines.get(3).status());
        assertEquals("Vitaminas D", lines.get(0).name());
        assertEquals("ng/ml", lines.get(0).unit());
        assertEquals("30 - 100", lines.get(0).reference());
        assertEquals(2, report.tests().get(0).outOfRange());
        assertEquals(2, report.totalOutOfRange());
        assertEquals("Antakalnio poliklinika", report.tests().get(0).laboratory());
        assertEquals("Antakpol.pdf", report.tests().get(0).fileName());
    }

    @Test
    void TA75_testDateFallsBackToTheUploadDate() {
        signedInUser(null);
        OffsetDateTime uploaded = OffsetDateTime.now().minusDays(2);
        BloodTest test = test(3L, "BeDatos.pdf", null, null, uploaded);
        when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(test));

        SummaryReport report = service.buildReport(null, jwt);

        assertEquals(uploaded.toLocalDate(), report.tests().get(0).date());
        assertTrue(report.tests().get(0).indicators().isEmpty());
    }

    @Test
    void TA75_thePeriodLeavesOutOlderTests() {
        signedInUser(null);
        BloodTest recent = test(1L, "naujas.pdf", null, today.minusDays(20), OffsetDateTime.now());
        BloodTest old = test(2L, "senas.pdf", null, today.minusMonths(8), OffsetDateTime.now().minusMonths(8));
        when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(recent, old));

        SummaryReport threeMonths = service.buildReport(
                new SummaryExportRequest(SummaryPeriod.THREE_MONTHS, List.of("TESTS")), jwt);
        SummaryReport all = service.buildReport(new SummaryExportRequest(SummaryPeriod.ALL, List.of("TESTS")), jwt);
        SummaryReport oneYear = service.buildReport(new SummaryExportRequest(SummaryPeriod.ONE_YEAR, List.of("TESTS")), jwt);

        assertEquals(1, threeMonths.tests().size());
        assertEquals("naujas.pdf", threeMonths.tests().get(0).fileName());
        assertEquals(2, all.tests().size());
        assertEquals(2, oneYear.tests().size());
        assertEquals("Paskutiniai 3 mėn.", threeMonths.periodLabel());
        assertEquals("Paskutiniai 12 mėn.", oneYear.periodLabel());
        assertEquals("Paskutiniai 6 mėn.",
                service.buildReport(new SummaryExportRequest(SummaryPeriod.SIX_MONTHS, List.of("TESTS")), jwt).periodLabel());
    }

    @Test
    void TA75_aiExplanationsAreTakenOnlyFromCompletedAnalyses() {
        signedInUser(null);
        BloodTest done = test(1L, "a.pdf", null, today.minusDays(5), OffsetDateTime.now());
        BloodTest failed = test(2L, "b.pdf", null, today.minusDays(6), OffsetDateTime.now());
        when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(done, failed));
        List<BloodTestIndicator> doneIndicators = List.of(
                indicator(11L, "Vitaminas D", "18", "ng/ml", "30 - 100"),
                indicator(12L, "Hemoglobinas", "135", "g/l", "120 - 160"));
        List<BloodTestIndicator> failedIndicators = List.of(indicator(21L, "Feritinas", "8", "ng/ml", "15 - 150"));
        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(1L)).thenReturn(doneIndicators);
        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(2L)).thenReturn(failedIndicators);
        BloodTestAnalysis completed = mock(BloodTestAnalysis.class);
        when(completed.getId()).thenReturn(100L);
        when(completed.getStatus()).thenReturn(BloodTestAnalysis.Status.COMPLETED);
        BloodTestAnalysis notDone = mock(BloodTestAnalysis.class);
        when(notDone.getId()).thenReturn(200L);
        when(notDone.getStatus()).thenReturn(BloodTestAnalysis.Status.FAILED);
        when(analysisRepository.findByBloodTestId(1L)).thenReturn(Optional.of(completed));
        when(analysisRepository.findByBloodTestId(2L)).thenReturn(Optional.of(notDone));
        IndicatorExplanation explanation = mock(IndicatorExplanation.class);
        when(explanation.getMeaning()).thenReturn("Vitamino D koncentracija žemesnė už normą.");
        when(explanationRepository.findByAnalysisIdAndIndicatorId(100L, 11L)).thenReturn(Optional.of(explanation));

        SummaryReport report = service.buildReport(new SummaryExportRequest(SummaryPeriod.ALL, List.of("ANALYSES")), jwt);

        assertEquals(1, report.analyses().size());
        assertEquals("Vitaminas D", report.analyses().get(0).indicatorName());
        assertEquals("Vitamino D koncentracija žemesnė už normą.", report.analyses().get(0).meaning());
        assertEquals(done.getTestDate(), report.analyses().get(0).testDate());
        assertTrue(report.includeAnalyses());
    }

    @Test
    void TA76_planUsesOnlyActivePlansAndCyclesInsideThePeriod() {
        signedInUser(null);
        PlanEntry running = entry(1L, "Vitaminas D3", "25", "mcg", today.minusDays(10), today.plusDays(20));
        PlanEntry finishedLongAgo = entry(2L, "Selenas", "55", "mcg", today.minusDays(150), today.minusDays(120));
        PlanEntry draftOnly = entry(4L, "Geležis", "14", "mg", today.minusDays(5), today.plusDays(25));
        when(planRepository.findAllForUser("google-1")).thenReturn(List.of(
                new PlanListRow(10L, 1L, ConsumptionPlan.Status.ACTIVE, null, null, "a.pdf", "Lab", today),
                new PlanListRow(11L, 1L, ConsumptionPlan.Status.DRAFT, null, null, "a.pdf", "Lab", today)));
        when(entryRepository.findByPlanIdOrderByIdAsc(10L)).thenReturn(List.of(running, finishedLongAgo));
        when(entryRepository.findByPlanIdOrderByIdAsc(11L)).thenReturn(List.of(draftOnly));

        SummaryReport all = service.buildReport(null, jwt);
        SummaryReport threeMonths = service.buildReport(
                new SummaryExportRequest(SummaryPeriod.THREE_MONTHS, List.of("SUPPLEMENT_PLAN")), jwt);

        assertEquals(List.of("Vitaminas D3", "Selenas"), all.plan().stream().map(PlanLine::supplement).toList());
        assertEquals(List.of("Vitaminas D3"), threeMonths.plan().stream().map(PlanLine::supplement).toList());
        assertEquals("25 mcg", all.plan().get(0).dose());
        assertEquals(running.getStartDate() + " – " + running.getEndDate(), all.plan().get(0).cycle());
    }

    @Test
    void TA76_adherenceUsesTheSameRulesAsTheDoseHistoryScreen() {
        signedInUser(null);
        PlanEntry vitamin = entry(1L, "Vitaminas D3", "25", "mcg", today.minusDays(10), today.plusDays(20));
        activePlan(vitamin);
        List<DoseLog> logs = new ArrayList<>();
        for (int daysAgo = 10; daysAgo >= 4; daysAgo--) {
            logs.add(dose(1L, today.minusDays(daysAgo), DoseLog.Status.TAKEN));
        }
        logs.add(dose(1L, today.minusDays(3), DoseLog.Status.SKIPPED));
        logs.add(dose(1L, today, DoseLog.Status.TAKEN));
        doseLogs(1L, logs);

        PlanLine line = service.buildReport(null, jwt).plan().get(0);

        assertEquals(8, line.taken(), "7 days before plus today, which was marked");
        assertEquals(1, line.skipped());
        assertEquals(2, line.unmarked(), "Two judged days were never marked");
        assertEquals(73, line.adherencePercent(), "8 taken out of 11 due doses");
    }

    @Test
    void TA76_todaysUnmarkedDoseIsNotCountedAsMissed() {
        signedInUser(null);
        PlanEntry magnesium = entry(2L, "Magnis", "200.0", "mg", today.minusDays(3), today.plusDays(30));
        activePlan(magnesium);
        doseLogs(2L, List.of(dose(2L, today.minusDays(2), DoseLog.Status.TAKEN)));

        PlanLine line = service.buildReport(null, jwt).plan().get(0);

        assertEquals("200 mg", line.dose(), "Trailing zeros are removed");
        assertEquals(1, line.taken());
        assertEquals(2, line.unmarked(), "Only the 3 days before today are due");
        assertEquals(33, line.adherencePercent());
    }

    @Test
    void TA76_aFinishedCycleWithFewTakenDosesShowsLowAdherence() {
        signedInUser(null);
        PlanEntry zinc = entry(3L, "Cinkas", "15", "mg", today.minusDays(60), today.minusDays(30));
        activePlan(zinc);
        List<DoseLog> logs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            logs.add(dose(3L, today.minusDays(60 - i), DoseLog.Status.TAKEN));
        }
        doseLogs(3L, logs);

        SummaryReport report = service.buildReport(null, jwt);
        PlanLine line = report.plan().get(0);

        assertEquals(4, line.taken());
        assertEquals(27, line.unmarked());
        assertEquals(13, line.adherencePercent());
        assertEquals(13, report.overallAdherence());
    }

    @Test
    void TA76_aCycleThatHasNotStartedHasNoAdherenceYet() {
        signedInUser(null);
        activePlan(entry(5L, "Cinkas", "15", "mg", today.plusDays(3), today.plusDays(33)));

        PlanLine line = service.buildReport(null, jwt).plan().get(0);

        assertEquals(0, line.taken() + line.skipped() + line.unmarked());
        assertNull(line.adherencePercent());
    }

    @Test
    void TA75_theRequestedSectionsDecideWhatIsCollected() {
        signedInUser(null);
        activePlan(entry(1L, "Vitaminas D3", "25", "mcg", today.minusDays(10), today.plusDays(20)));
        BloodTest test = test(1L, "a.pdf", null, today.minusDays(5), OffsetDateTime.now());
        when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(test));

        SummaryReport onlyTests = service.buildReport(new SummaryExportRequest(SummaryPeriod.ALL, List.of("tests")), jwt);
        SummaryReport onlyPlan = service.buildReport(
                new SummaryExportRequest(SummaryPeriod.ALL, List.of("SUPPLEMENT_PLAN")), jwt);
        SummaryReport everything = service.buildReport(null, jwt);

        assertTrue(onlyTests.includeTests());
        assertFalse(onlyTests.includePlan());
        assertEquals(1, onlyTests.tests().size());
        assertTrue(onlyTests.plan().isEmpty());
        assertTrue(onlyPlan.tests().isEmpty());
        assertEquals(1, onlyPlan.plan().size());
        assertTrue(everything.includeTests() && everything.includeAnalyses()
                && everything.includePlan() && everything.includeHistory());
    }

    @Test
    void TA75_theReportIsDrawnAsARealPdf() {
        signedInUser(null);
        BloodTest test = test(1L, "Antakpol.pdf", "Antakalnio poliklinika", today.minusDays(20), OffsetDateTime.now());
        when(bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(test));
        List<BloodTestIndicator> indicators = List.of(indicator(11L, "Vitaminas D", "18", "ng/ml", "30 - 100"));
        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(1L)).thenReturn(indicators);
        activePlan(entry(1L, "Vitaminas D3", "25", "mcg", today.minusDays(10), today.plusDays(20)));

        byte[] pdf = service.exportSummary(null, jwt);

        assertTrue(pdf.length > 5000);
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }
}
