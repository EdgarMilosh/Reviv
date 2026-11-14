package lt.reviv.bloodtests.service.extraction;

import lt.reviv.bloodtests.ai.extraction.ExtractedReport;
import lt.reviv.bloodtests.ai.OpenAiProperties;
import lt.reviv.bloodtests.ai.extraction.PdfExtractionClient;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.ExtractionIssue;

import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.ExtractionIssueRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BloodTestCorrectionService {

    private static final Logger log =
            LoggerFactory.getLogger(BloodTestCorrectionService.class);

    private final BloodTestRepository bloodTestRepository;
    private final BloodTestIndicatorRepository indicatorRepository;
    private final ExtractionIssueRepository issueRepository;
    private final PdfExtractionClient extractionClient;
    private final OpenAiProperties properties;
    private final TransactionTemplate transactionTemplate;

    public BloodTestCorrectionService(
            BloodTestRepository bloodTestRepository,
            BloodTestIndicatorRepository indicatorRepository,
            ExtractionIssueRepository issueRepository,
            PdfExtractionClient extractionClient,
            OpenAiProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.bloodTestRepository = bloodTestRepository;
        this.indicatorRepository = indicatorRepository;
        this.issueRepository = issueRepository;
        this.extractionClient = extractionClient;
        this.properties = properties;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    private record CorrectionJob(
            byte[] pdfData,
            List<ExtractedReport.CorrectionTarget> targets
    ) {}

    public void processCorrections(Long bloodTestId) {

        if (!properties.enabled()) {
            return;
        }

        try {
            // 1. Pasiruosiame duomenis atskiroje DB transakcijoje
            CorrectionJob job = transactionTemplate.execute(status -> {

                BloodTest test = bloodTestRepository
                        .findById(bloodTestId)
                        .orElseThrow();

                if (!"RETRY_QUEUED".equals(test.getStatus())) {
                    return null;
                }

                if (test.getExtractionAttempts()
                        >= properties.maxAttempts()) {
                    test.failExtraction();
                    return null;
                }

                List<ExtractionIssue> openIssues =
                        issueRepository
                                .findByBloodTestIdAndResolvedFalse(
                                        bloodTestId
                                );

                if (openIssues.isEmpty()) {
                    throw new IllegalStateException(
                            "Nera pazymetu klaidu."
                    );
                }

                // Grupavimas pagal rodiklio ID
                Map<Long, List<ExtractionIssue>> grouped =
                        new LinkedHashMap<>();

                for (ExtractionIssue issue : openIssues) {
                    grouped.computeIfAbsent(
                            issue.getIndicatorId(),
                            ignored -> new ArrayList<>()
                    ).add(issue);
                }

                List<ExtractedReport.CorrectionTarget> targets =
                        new ArrayList<>();

                for (var entry : grouped.entrySet()) {

                    BloodTestIndicator indicator =
                            indicatorRepository
                                    .findByIdAndBloodTestId(
                                            entry.getKey(),
                                            bloodTestId
                                    )
                                    .orElseThrow();

                    String fields = entry.getValue()
                            .stream()
                            .map(ExtractionIssue::getFieldName)
                            .distinct()
                            .collect(Collectors.joining(","));

                    targets.add(
                            new ExtractedReport.CorrectionTarget(
                                    indicator.getId(),
                                    indicator.getName(),
                                    indicator.getRawValue(),
                                    indicator.getUnit(),
                                    indicator.getReferenceInterval(),
                                    fields,
                                    null
                            )
                    );
                }

                test.startProcessing();

                return new CorrectionJob(
                        test.getPdfData(),
                        List.copyOf(targets)
                );
            });

            if (job == null) {
                return;
            }

            // 2. OpenAI kvieciame BE DB transakcijos
            List<ExtractedReport.Correction> corrections =
                    extractionClient.correct(
                            job.pdfData(),
                            job.targets()
                    );

            // 3. Rezultatus issaugome naujoje transakcijoje
            transactionTemplate.executeWithoutResult(status -> {

                BloodTest test = bloodTestRepository
                        .findById(bloodTestId)
                        .orElseThrow();

                if (!"PROCESSING".equals(test.getStatus())) {
                    throw new IllegalStateException(
                            "Netinkama tyrimo busena."
                    );
                }

                List<ExtractionIssue> openIssues =
                        issueRepository
                                .findByBloodTestIdAndResolvedFalse(
                                        bloodTestId
                                );

                Set<Long> expectedIds = new HashSet<>();

                for (ExtractionIssue issue : openIssues) {
                    expectedIds.add(issue.getIndicatorId());
                }

                Map<Long, ExtractedReport.Correction> byId =
                        new HashMap<>();

                if (corrections == null) {
                    throw new IllegalStateException(
                            "DI negrazino pataisymu."
                    );
                }

                for (var correction : corrections) {

                    if (!expectedIds.contains(correction.targetId())
                            || byId.putIfAbsent(
                            correction.targetId(),
                            correction
                    ) != null) {

                        throw new IllegalStateException(
                                "DI grazino netinkamus rodikliu ID."
                        );
                    }
                }

                if (!byId.keySet().equals(expectedIds)) {
                    throw new IllegalStateException(
                            "DI negrazino visu pazymetu rodikliu."
                    );
                }

                // 4. Keiciame TIK pazymetus laukus
                for (ExtractionIssue issue : openIssues) {

                    BloodTestIndicator original =
                            indicatorRepository
                                    .findByIdAndBloodTestId(
                                            issue.getIndicatorId(),
                                            bloodTestId
                                    )
                                    .orElseThrow();

                    ExtractedReport.Correction corrected =
                            byId.get(issue.getIndicatorId());

                    BloodTestIndicator replacement =
                            new BloodTestIndicator(
                                    bloodTestId,
                                    corrected.name(),
                                    corrected.rawValue(),
                                    corrected.unit(),
                                    corrected.referenceInterval()
                            );

                    validateField(
                            issue.getFieldName(),
                            replacement
                    );

                    original.replaceField(
                            issue.getFieldName(),
                            replacement
                    );

                    // Klaida lieka istorijoje
                    issue.resolve();
                }

                // 5. Graziname tyrima naudotojo perziurai
                test.finishExtraction(
                        test.getLaboratory(),
                        test.getTestDate()
                );
            });

            log.info(
                    "Tyrimo {} pazymetu rodikliu pataisymas baigtas.",
                    bloodTestId
            );

        } catch (Exception e) {

            // Jautriu PDF ir DI duomenu i logus nerasome
            log.warn(
                    "Tyrimo {} taisymo klaida: {}",
                    bloodTestId,
                    e.getClass().getSimpleName()
            );

            transactionTemplate.executeWithoutResult(status -> {

                BloodTest test = bloodTestRepository
                        .findById(bloodTestId)
                        .orElseThrow();

                if ("PROCESSING".equals(test.getStatus())
                        || "RETRY_QUEUED".equals(test.getStatus())) {
                    test.failExtraction();
                }
            });
        }
    }

    private void validateField(
            String field,
            BloodTestIndicator indicator
    ) {
        String value = switch (field) {
            case "NAME" -> indicator.getName();
            case "VALUE" -> indicator.getRawValue();
            case "UNIT" -> indicator.getUnit();
            case "REFERENCE" -> indicator.getReferenceInterval();
            default -> throw new IllegalArgumentException(
                    "Nezinomas klaidos laukas."
            );
        };

        int maxLength = switch (field) {
            case "NAME" -> 255;
            case "VALUE" -> 128;
            case "UNIT" -> 80;
            case "REFERENCE" -> 160;
            default -> throw new IllegalArgumentException();
        };

        if (value != null && value.length() > maxLength) {
            throw new IllegalStateException(
                    "DI grazino per ilga lauko reiksme."
            );
        }

        if (("NAME".equals(field) || "VALUE".equals(field))
                && (value == null || value.isBlank())) {
            throw new IllegalStateException(
                    "DI negrazino privalomos reiksmes."
            );
        }
    }
}
