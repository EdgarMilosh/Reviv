package lt.reviv.bloodtests.service.summary;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
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
import lt.reviv.bloodtests.service.IndicatorStatusCalculator;
import lt.reviv.bloodtests.service.summary.SummaryReport.AnalysisNote;
import lt.reviv.bloodtests.service.summary.SummaryReport.IndicatorLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.Patient;
import lt.reviv.bloodtests.service.summary.SummaryReport.PlanLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.TestSection;
import lt.reviv.doses.domain.DoseLog;
import lt.reviv.doses.repository.DoseLogRepository;
import lt.reviv.doses.service.AdherenceCalculator;
import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** US-14: collects the data of the doctor summary and draws it as a PDF ({@link SummaryPdfRenderer}). */
@Service
public class SummaryExportService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vilnius");
    private static final String MISSING = "nenurodyta";

    private static final Set<String> DEFAULT_SECTIONS = Set.of(
            "TESTS",
            "ANALYSES",
            "SUPPLEMENT_PLAN",
            "CONSUMPTION_HISTORY"
    );

    private final AppUserRepository appUserRepository;
    private final ProfileRepository profileRepository;
    private final BloodTestRepository bloodTestRepository;
    private final BloodTestIndicatorRepository indicatorRepository;
    private final BloodTestAnalysisRepository analysisRepository;
    private final IndicatorExplanationRepository explanationRepository;
    private final ConsumptionPlanRepository planRepository;
    private final PlanEntryRepository entryRepository;
    private final DoseLogRepository doseLogRepository;

    public SummaryExportService(
            AppUserRepository appUserRepository,
            ProfileRepository profileRepository,
            BloodTestRepository bloodTestRepository,
            BloodTestIndicatorRepository indicatorRepository,
            BloodTestAnalysisRepository analysisRepository,
            IndicatorExplanationRepository explanationRepository,
            ConsumptionPlanRepository planRepository,
            PlanEntryRepository entryRepository,
            DoseLogRepository doseLogRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.profileRepository = profileRepository;
        this.bloodTestRepository = bloodTestRepository;
        this.indicatorRepository = indicatorRepository;
        this.analysisRepository = analysisRepository;
        this.explanationRepository = explanationRepository;
        this.planRepository = planRepository;
        this.entryRepository = entryRepository;
        this.doseLogRepository = doseLogRepository;
    }

    @Transactional(readOnly = true)
    public byte[] exportSummary(SummaryExportRequest request, Jwt jwt) {
        return SummaryPdfRenderer.render(buildReport(request, jwt));
    }

    /** Collects everything the PDF shows. Kept apart from the drawing so that it can be tested without a PDF. */
    @Transactional(readOnly = true)
    public SummaryReport buildReport(SummaryExportRequest request, Jwt jwt) {
        AppUser user = appUserRepository.findByGoogleId(jwt.getSubject())
                .orElseThrow(() -> new IllegalArgumentException("Naudotojas nerastas."));

        LocalDate today = LocalDate.now(ZONE);
        SummaryPeriod period = request == null || request.period() == null ? SummaryPeriod.ALL : request.period();
        LocalDate periodFrom = resolveStart(period, today);
        Set<String> sections = request == null || request.sections() == null || request.sections().isEmpty()
                ? DEFAULT_SECTIONS
                : new LinkedHashSet<>(request.sections().stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .toList());
        boolean includeTests = sections.contains("TESTS");
        boolean includeAnalyses = sections.contains("ANALYSES");
        boolean includePlan = sections.contains("SUPPLEMENT_PLAN");
        boolean includeHistory = sections.contains("CONSUMPTION_HISTORY");

        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        List<BloodTest> tests = bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(user.getId()).stream()
                .filter(test -> periodFrom == null || !testDate(test).isBefore(periodFrom))
                .toList();
        List<PlanListRow> plans = planRepository.findAllForUser(jwt.getSubject());

        List<TestSection> testSections = new ArrayList<>();
        if (includeTests || includeAnalyses) {
            for (BloodTest test : tests) {
                testSections.add(new TestSection(test.getFileName(), test.getLaboratory(), testDate(test),
                        indicatorLines(test)));
            }
        }

        List<AnalysisNote> notes = new ArrayList<>();
        if (includeAnalyses) {
            for (BloodTest test : tests) {
                notes.addAll(analysisNotes(test));
            }
        }

        List<PlanLine> planLines = new ArrayList<>();
        if (includePlan || includeHistory) {
            for (PlanListRow plan : plans) {
                if (plan.status() != ConsumptionPlan.Status.ACTIVE) {
                    continue;
                }
                for (PlanEntry entry : entryRepository.findByPlanIdOrderByIdAsc(plan.id())) {
                    if (periodFrom == null || !entry.getEndDate().isBefore(periodFrom)) {
                        planLines.add(planLine(entry, periodFrom, today));
                    }
                }
            }
        }

        return new SummaryReport(today, periodLabel(period), patient(user, profile, today),
                includeTests, includeAnalyses, includePlan, includeHistory, testSections, notes, planLines);
    }

    private List<IndicatorLine> indicatorLines(BloodTest test) {
        List<IndicatorLine> lines = new ArrayList<>();
        for (BloodTestIndicator indicator : indicatorRepository.findByBloodTestIdOrderByIdAsc(test.getId())) {
            lines.add(new IndicatorLine(indicator.getName(), indicator.getRawValue(), indicator.getUnit(),
                    indicator.getReferenceInterval(),
                    IndicatorStatusCalculator.calculate(indicator.getRawValue(), indicator.getReferenceInterval())));
        }
        return lines;
    }

    private List<AnalysisNote> analysisNotes(BloodTest test) {
        Optional<BloodTestAnalysis> analysis = analysisRepository.findByBloodTestId(test.getId());
        if (analysis.isEmpty() || analysis.get().getStatus() != BloodTestAnalysis.Status.COMPLETED) {
            return List.of();
        }
        List<AnalysisNote> notes = new ArrayList<>();
        for (BloodTestIndicator indicator : indicatorRepository.findByBloodTestIdOrderByIdAsc(test.getId())) {
            explanationRepository.findByAnalysisIdAndIndicatorId(analysis.get().getId(), indicator.getId())
                    .ifPresent(explanation ->
                            notes.add(new AnalysisNote(testDate(test), indicator.getName(), explanation.getMeaning())));
        }
        return notes;
    }

    /**
     * Same counting rules as the dose history screen: days up to yesterday are due, today counts only once it was
     * marked, and a dose that was not marked counts as not taken.
     */
    private PlanLine planLine(PlanEntry entry, LocalDate periodFrom, LocalDate today) {
        LocalDate from = periodFrom == null || periodFrom.isBefore(entry.getStartDate())
                ? entry.getStartDate() : periodFrom;
        List<DoseLog> logs = doseLogRepository
                .findByPlanEntryIdInAndDoseDateBetween(List.of(entry.getId()), from, today).stream()
                .filter(log -> !log.getDoseDate().isBefore(entry.getStartDate())
                        && !log.getDoseDate().isAfter(entry.getEndDate()))
                .toList();
        long taken = logs.stream().filter(log -> log.getStatus() == DoseLog.Status.TAKEN).count();
        long skipped = logs.size() - taken;
        long planned = AdherenceCalculator.plannedDoses(
                entry.getStartDate(), entry.getEndDate(), from, today, today.minusDays(1));
        if (logs.stream().anyMatch(log -> log.getDoseDate().equals(today))) {
            planned++;
        }
        long unmarked = Math.max(0, planned - taken - skipped);
        return new PlanLine(entry.getSupplementNameSnapshot(), formatDose(entry.getDailyDose(), entry.getDoseUnit()),
                entry.getStartDate() + " – " + entry.getEndDate(), taken, skipped, unmarked,
                AdherenceCalculator.percent(taken, planned));
    }

    private Patient patient(AppUser user, Profile profile, LocalDate today) {
        return new Patient(
                safe(user.getName()),
                safe(user.getEmail()),
                formatAge(profile, today),
                profile == null || profile.getHeightCm() == null ? MISSING : profile.getHeightCm() + " cm",
                profile == null || profile.getWeightKg() == null
                        ? MISSING : profile.getWeightKg().stripTrailingZeros().toPlainString() + " kg",
                formatList(profile == null ? null : profile.getAllergies()),
                formatList(profile == null ? null : profile.getHarmfulHabits()));
    }

    private static LocalDate testDate(BloodTest test) {
        return test.getTestDate() != null ? test.getTestDate() : test.getUploadedAt().toLocalDate();
    }

    private static LocalDate resolveStart(SummaryPeriod period, LocalDate today) {
        return switch (period) {
            case THREE_MONTHS -> today.minusMonths(3);
            case SIX_MONTHS -> today.minusMonths(6);
            case ONE_YEAR -> today.minusYears(1);
            case ALL -> null;
        };
    }

    private static String periodLabel(SummaryPeriod period) {
        return switch (period) {
            case THREE_MONTHS -> "Paskutiniai 3 mėn.";
            case SIX_MONTHS -> "Paskutiniai 6 mėn.";
            case ONE_YEAR -> "Paskutiniai 12 mėn.";
            case ALL -> "Visas laikotarpis";
        };
    }

    private static String formatDose(BigDecimal dose, String unit) {
        String value = dose == null ? "–" : dose.stripTrailingZeros().toPlainString();
        return unit == null || unit.isBlank() ? value : value + " " + unit;
    }

    private static String formatAge(Profile profile, LocalDate today) {
        if (profile == null || profile.getBirthDate() == null || profile.getBirthDate().isAfter(today)) {
            return MISSING;
        }
        return Period.between(profile.getBirthDate(), today).getYears() + " m.";
    }

    private static String formatList(String[] values) {
        if (values == null || values.length == 0) {
            return MISSING;
        }
        return String.join(", ", values);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
