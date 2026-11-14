package lt.reviv.bloodtests.service;

import lt.reviv.bloodtests.ai.ExtractedReport;
import lt.reviv.bloodtests.ai.OpenAiProperties;
import lt.reviv.bloodtests.ai.PdfExtractionClient;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class BloodTestExtractionService {

    private static final Logger log =
            LoggerFactory.getLogger(BloodTestExtractionService.class);

    private final BloodTestRepository bloodTestRepository;
    private final BloodTestIndicatorRepository indicatorRepository;
    private final PdfExtractionClient extractionClient;
    private final OpenAiProperties properties;
    private final TransactionTemplate transactionTemplate;

    public BloodTestExtractionService(
            BloodTestRepository bloodTestRepository,
            BloodTestIndicatorRepository indicatorRepository,
            PdfExtractionClient extractionClient,
            OpenAiProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.bloodTestRepository = bloodTestRepository;
        this.indicatorRepository = indicatorRepository;
        this.extractionClient = extractionClient;
        this.properties = properties;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }



    public void process(Long bloodTestId) {

        if (!properties.enabled()) {
            return;
        }

        byte[] pdfData = transactionTemplate.execute(status -> {

            BloodTest test = bloodTestRepository.findById(bloodTestId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tyrimas nerastas."
                    ));

            if (!"UPLOADED".equals(test.getStatus())) {
                return null;
            }

            if (test.getExtractionAttempts()
                    >= properties.maxAttempts()) {

                test.failExtraction();
                return null;
            }

            test.startProcessing();

            return test.getPdfData();
        });

        if (pdfData == null) {
            return;
        }

        try {
            validatePdfLimits(pdfData);

            ExtractedReport report =
                    extractionClient.extract(pdfData);

            validateReport(report);

            LocalDate testDate = parseDate(report.testDate());

            transactionTemplate.executeWithoutResult(status -> {

                BloodTest test = bloodTestRepository.findById(bloodTestId)
                        .orElseThrow();

                if (!"PROCESSING".equals(test.getStatus())) {
                    throw new IllegalStateException(
                            "Netinkama tyrimo busena."
                    );
                }

                indicatorRepository.deleteByBloodTestId(bloodTestId);
                indicatorRepository.flush();

                List<BloodTestIndicator> indicators =
                        report.indicators().stream()
                                .map(i -> new BloodTestIndicator(
                                        bloodTestId,
                                        i.name(),
                                        i.rawValue(),
                                        i.unit(),
                                        i.referenceInterval()
                                ))
                                .toList();

                indicatorRepository.saveAll(indicators);

                test.finishExtraction(
                        report.laboratory(),
                        testDate
                );
            });

            log.info(
                    "Tyrimo {} duomenu istraukimas baigtas.",
                    bloodTestId
            );

        } catch (Exception e) {

            log.warn(
                    "Tyrimo {} istraukimo klaida: {} - {}",
                    bloodTestId,
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );

            transactionTemplate.executeWithoutResult(status -> {

                BloodTest test = bloodTestRepository.findById(bloodTestId)
                        .orElseThrow();

                if ("PROCESSING".equals(test.getStatus())) {
                    test.failExtraction();
                }
            });
        }
    }

    private void validatePdfLimits(byte[] pdfData) {

        if (pdfData.length > properties.maxPdfBytes()) {
            throw new IllegalArgumentException(
                    "PDF virsija AI apdorojimo dydzio limita."
            );
        }

        try (PDDocument document = Loader.loadPDF(pdfData)) {

            int pages = document.getNumberOfPages();

            if (pages == 0 || pages > properties.maxPdfPages()) {
                throw new IllegalArgumentException(
                        "PDF puslapiu skaicius netinkamas."
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Nepavyko perskaityti PDF.", e
            );
        }
    }

    private void validateReport(ExtractedReport report) {

        if (report == null
                || report.indicators() == null
                || report.indicators().isEmpty()) {
            throw new IllegalArgumentException(
                    "DI neistrauke tyrimo rodikliu."
            );
        }

        checkLength(report.laboratory(), 255);

        for (ExtractedReport.Indicator indicator
                : report.indicators()) {

            if (indicator.name() == null
                    || indicator.name().isBlank()
                    || indicator.rawValue() == null
                    || indicator.rawValue().isBlank()) {
                throw new IllegalArgumentException(
                        "DI grazino nepilnus rodiklio duomenis."
                );
            }

            checkLength(indicator.name(), 255);
            checkLength(indicator.rawValue(), 128);
            checkLength(indicator.unit(), 80);
            checkLength(indicator.referenceInterval(), 160);
        }
    }

    private void checkLength(String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "DI grazino per ilga lauko reiksme."
            );
        }
    }

    private LocalDate parseDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd.MM.uuuu")
        );

        for (DateTimeFormatter format : formats) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }
}
