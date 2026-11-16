package lt.reviv.bloodtests.service.summary;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.IndicatorExplanation;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.dto.summary.SummaryExportRequest;
import lt.reviv.bloodtests.dto.summary.SummaryPeriod;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.IndicatorExplanationRepository;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.doses.domain.DoseLog;
import lt.reviv.doses.repository.DoseLogRepository;
import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SummaryExportService {

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
        AppUser user = appUserRepository.findByGoogleId(jwt.getSubject())
                .orElseThrow(() -> new IllegalArgumentException("Naudotojas nerastas."));

        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        LocalDate periodFrom = resolveStart(request == null ? SummaryPeriod.ALL : request.period());
        Set<String> sections = request == null || request.sections() == null || request.sections().isEmpty()
                ? DEFAULT_SECTIONS
                : new LinkedHashSet<>(request.sections().stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .toList());

        List<BloodTest> tests = bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(user.getId()).stream()
                .filter(test -> {
                    LocalDate testDate = test.getTestDate() != null ? test.getTestDate() : test.getUploadedAt().toLocalDate();
                    return periodFrom == null || !testDate.isBefore(periodFrom);
                })
                .toList();

        List<PlanEntry> entries = planRepository.findAllForUser(jwt.getSubject()).stream()
                .flatMap(plan -> entryRepository.findByPlanIdOrderByIdAsc(plan.id()).stream())
                .toList();

        List<String> usageDetails = new ArrayList<>();
        if (periodFrom != null) {
            for (PlanEntry entry : entries) {
                List<DoseLog> logs = doseLogRepository.findByPlanEntryIdInAndDoseDateBetween(List.of(entry.getId()), periodFrom, LocalDate.now());
                long taken = logs.stream().filter(log -> log.getStatus() == DoseLog.Status.TAKEN).count();
                long skipped = logs.stream().filter(log -> log.getStatus() == DoseLog.Status.SKIPPED).count();
                long total = Math.max(1L, taken + skipped);
                double adherence = Math.min(100d, (taken * 100d) / total);
                usageDetails.add("- " + entry.getSupplementNameSnapshot() + ": " + entry.getDailyDose() + " " + entry.getDoseUnit()
                        + ", ciklas " + entry.getStartDate() + "–" + entry.getEndDate() + ", nuoseklumas " + String.format(Locale.US, "%.0f", adherence) + "%");
            }
        }

        List<String> analysisSummaries = new ArrayList<>();
        for (BloodTest test : tests) {
            var analysis = analysisRepository.findByBloodTestId(test.getId());
            if (analysis.isEmpty() || analysis.get().getStatus() != BloodTestAnalysis.Status.COMPLETED) {
                continue;
            }
            for (BloodTestIndicator indicator : indicatorRepository.findByBloodTestIdOrderByIdAsc(test.getId())) {
                IndicatorExplanation explanation = explanationRepository.findByAnalysisIdAndIndicatorId(analysis.get().getId(), indicator.getId()).orElse(null);
                if (explanation != null) {
                    analysisSummaries.add("- " + indicator.getName() + " — DI turinys: " + explanation.getMeaning());
                }
            }
        }

        return buildPdf(user, profile, tests, sections, usageDetails, analysisSummaries);
    }

    private LocalDate resolveStart(SummaryPeriod period) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? SummaryPeriod.ALL : period) {
            case THREE_MONTHS -> today.minusMonths(3);
            case SIX_MONTHS -> today.minusMonths(6);
            case ONE_YEAR -> today.minusYears(1);
            case ALL -> null;
        };
    }

    private byte[] buildPdf(
            AppUser user,
            Profile profile,
            List<BloodTest> tests,
            Set<String> sections,
            List<String> usageDetails,
            List<String> analysisSummaries
    ) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfComposer composer = new PdfComposer(document)) {
            composer.addHeading("Reviv — gydytojo suvestinė");
            composer.addText("Sugeneruota: " + LocalDate.now());
            composer.addEmptyLine();

            composer.addSection("Naudotojo profilis");
            composer.addText("Vardas / el. paštas: " + safe(user.getName(), "-") + " / " + safe(user.getEmail(), "-"));
            composer.addText("Amžius: " + formatAge(profile));
            composer.addText("Ūgis: " + formatValue(profile != null ? profile.getHeightCm() : null, "cm"));
            composer.addText("Svoris: " + formatValue(profile != null ? profile.getWeightKg() : null, "kg"));
            composer.addText("Alergijos: " + formatList(profile != null ? profile.getAllergies() : new String[0]));
            composer.addText("Žalingi įpročiai: " + formatList(profile != null ? profile.getHarmfulHabits() : new String[0]));
            composer.addEmptyLine();

            if (sections.contains("TESTS") || sections.contains("ANALYSES")) {
                composer.addSection("Tyrimai ir rodikliai");
                if (tests.isEmpty()) {
                    composer.addText("Nėra įtrauktų tyrimų pasirinktu laikotarpiu.");
                } else {
                    for (BloodTest test : tests) {
                        String when = test.getTestDate() != null ? test.getTestDate().toString() : test.getUploadedAt().toLocalDate().toString();
                        composer.addText("- " + test.getFileName() + " | " + safe(test.getLaboratory(), "-") + " | " + when);
                        List<BloodTestIndicator> indicators = indicatorRepository.findByBloodTestIdOrderByIdAsc(test.getId());
                        if (indicators.isEmpty()) {
                            composer.addText("  Nėra rodiklių.");
                            continue;
                        }
                        for (BloodTestIndicator indicator : indicators) {
                            String deviation = indicator.getReferenceInterval() == null || indicator.getReferenceInterval().isBlank()
                                    ? "referencinis intervalas nenurodytas"
                                    : "ref. " + indicator.getReferenceInterval();
                            composer.addText("  • " + indicator.getName() + ": " + safe(indicator.getRawValue(), "-")
                                    + " " + safe(indicator.getUnit(), "") + " | " + deviation);
                        }
                    }
                }
                composer.addEmptyLine();
            }

            if (sections.contains("ANALYSES")) {
                composer.addSection("DI paaiškinimai");
                if (analysisSummaries.isEmpty()) {
                    composer.addText("Nėra DI paaiškinimų pasirinktame laikotarpyje.");
                } else {
                    for (String summary : analysisSummaries) {
                        composer.addText(summary);
                    }
                }
                composer.addEmptyLine();
            }

            if (sections.contains("SUPPLEMENT_PLAN") || sections.contains("CONSUMPTION_HISTORY")) {
                composer.addSection("Papildai ir vartojimo istorija");
                if (usageDetails.isEmpty()) {
                    composer.addText("Nėra papildų vartojimo įrašų pasirinktu laikotarpiu.");
                } else {
                    for (String detail : usageDetails) {
                        composer.addText(detail);
                    }
                }
                composer.addEmptyLine();
            }

            composer.addText("Pastaba: suvestinė nėra diagnozė.");
            composer.addText("DI turinys pažymėtas kaip DI turinys.");
            composer.addFooter();
            composer.close();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nepavyko sugeneruoti suvestinės PDF.", e);
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatAge(Profile profile) {
        if (profile == null || profile.getBirthDate() == null) {
            return "nenurodyta";
        }
        LocalDate today = LocalDate.now();
        if (profile.getBirthDate().isAfter(today)) {
            return "nenurodyta";
        }
        return Period.between(profile.getBirthDate(), today).getYears() + " metai";
    }

    private String formatValue(Integer value, String suffix) {
        return value == null ? "nenurodyta" : value + " " + suffix;
    }

    private String formatValue(BigDecimal value, String suffix) {
        return value == null ? "nenurodyta" : value.stripTrailingZeros() + " " + suffix;
    }

    private String formatList(String[] values) {
        if (values == null || values.length == 0) {
            return "nenurodyta";
        }
        return String.join(", ", values);
    }

    private static final class PdfComposer implements AutoCloseable {
        private final PDDocument document;
        private PDPage currentPage;
        private PDPageContentStream contentStream;
        private float yCursor;

        PdfComposer(PDDocument document) throws IOException {
            this.document = document;
            nextPage();
        }

        void addHeading(String text) throws IOException {
            addTextWithStyle(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18f);
            addEmptyLine();
        }

        void addSection(String text) throws IOException {
            addTextWithStyle(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14f);
        }

        void addText(String text) throws IOException {
            addTextWithStyle(text, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11f);
        }

        void addEmptyLine() throws IOException {
            yCursor -= 14f;
        }

        void addFooter() throws IOException {
            addTextWithStyle("Reviv • suvestinė gydytojui", new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 9f);
            yCursor -= 10f;
            addTextWithStyle("Suvestinė nėra diagnozė.", new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9f);
        }

        private void addTextWithStyle(String text, PDType1Font font, float fontSize) throws IOException {
            if (text == null || text.isBlank()) {
                return;
            }
            String normalized = normalizePdfText(text);
            for (String line : wrapText(normalized, 92)) {
                ensureSpace(16f);
                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.newLineAtOffset(50f, yCursor);
                contentStream.showText(line);
                contentStream.endText();
                yCursor -= fontSize + 3f;
            }
        }

        private String normalizePdfText(String value) {
            String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replaceAll("[\\p{InCombiningDiacriticalMarks}&&[^\\p{ASCII}]]", "");
            return normalized.replace('ą', 'a')
                    .replace('č', 'c')
                    .replace('ę', 'e')
                    .replace('ė', 'e')
                    .replace('į', 'i')
                    .replace('š', 's')
                    .replace('ų', 'u')
                    .replace('ū', 'u')
                    .replace('ž', 'z')
                    .replace('Ą', 'A')
                    .replace('Č', 'C')
                    .replace('Ę', 'E')
                    .replace('Ė', 'E')
                    .replace('Į', 'I')
                    .replace('Š', 'S')
                    .replace('Ų', 'U')
                    .replace('Ū', 'U')
                    .replace('Ž', 'Z');
        }

        private List<String> wrapText(String text, int maxChars) {
            List<String> result = new ArrayList<>();
            String[] words = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (candidate.length() > maxChars) {
                    if (!line.isEmpty()) {
                        result.add(line.toString());
                        line = new StringBuilder(word);
                    } else {
                        result.add(word);
                        line = new StringBuilder();
                    }
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) {
                result.add(line.toString());
            }
            return result;
        }

        private void ensureSpace(float requested) throws IOException {
            if (yCursor - requested < 50f) {
                contentStream.close();
                nextPage();
            }
        }

        private void nextPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            contentStream = new PDPageContentStream(document, currentPage);
            yCursor = 780f;
        }

        @Override
        public void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }
    }
}
