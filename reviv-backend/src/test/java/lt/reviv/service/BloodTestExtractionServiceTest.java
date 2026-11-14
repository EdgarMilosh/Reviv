package lt.reviv.service;

import lt.reviv.bloodtests.ai.extraction.ExtractedReport;
import lt.reviv.bloodtests.ai.OpenAiProperties;
import lt.reviv.bloodtests.ai.extraction.OpenAiPdfExtractionClient;
import lt.reviv.bloodtests.ai.extraction.PdfExtractionClient;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.service.extraction.BloodTestExtractionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloodTestExtractionServiceTest {
    @Mock BloodTestRepository tests;
    @Mock BloodTestIndicatorRepository indicators;
    @Mock
    PdfExtractionClient client;
    @Mock PlatformTransactionManager txManager;

    BloodTestExtractionService service;
    BloodTest test;
    byte[] pdf;

    static byte[] validPdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    static ExtractedReport report(String date) {
        return new ExtractedReport("Laboratorija", date,
                List.of(new ExtractedReport.Indicator("HGB", "104", "g/L", "117-145"),
                        new ExtractedReport.Indicator("WBC", "8,93", "10^9/L", "4-9.8")));
    }

    private BloodTestExtractionService create(OpenAiProperties settings) {
        return new BloodTestExtractionService(tests, indicators, client, settings, txManager);
    }

    @BeforeEach
    void init() throws Exception {
        pdf = validPdf(1);
        test = new BloodTest(7L, "report.pdf", (long)pdf.length, "sha", pdf);
        ReflectionTestUtils.setField(test, "id", 6L);
        when(tests.findById(6L)).thenReturn(Optional.of(test));
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        service = create(new OpenAiProperties(true, "fake", "gpt-5-nano", 5, 3_000_000, 2, 4000));
    }

    @Test void successfulExtractionPersistsRowsAndDate() {
        when(client.extract(any())).thenReturn(report("2026-11-14"));
        service.process(6L);
        assertEquals("NEEDS_REVIEW", test.getStatus());
        assertEquals(1, test.getExtractionAttempts());
        assertEquals("Laboratorija", test.getLaboratory());
        assertEquals(LocalDate.of(2026, 11, 14), test.getTestDate());
        verify(indicators).deleteByBloodTestId(6L);
        verify(indicators).flush();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BloodTestIndicator>> captor = ArgumentCaptor.forClass(List.class);
        verify(indicators).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals("HGB", captor.getValue().getFirst().getName());
        assertEquals("104", captor.getValue().getFirst().getRawValue());
    }
    @Test void dateFromLithuanianFormatIsParsed() {
        when(client.extract(any())).thenReturn(report("14.11.2026"));
        service.process(6L);
        assertEquals(LocalDate.of(2026,11,14), test.getTestDate());
    }
    @Test void unparseableDateIsAbsentButReportStillSaved() {
        when(client.extract(any())).thenReturn(report("invalid date"));
        service.process(6L);
        assertEquals("NEEDS_REVIEW", test.getStatus());
        assertNull(test.getTestDate());
    }
    @Test void nullDateAndLabAreAccepted() {
        when(client.extract(any())).thenReturn(new ExtractedReport(null, null,
                List.of(new ExtractedReport.Indicator("WBC", "8", null, null))));
        service.process(6L);
        assertEquals("NEEDS_REVIEW", test.getStatus());
        assertNull(test.getTestDate());
    }
    @Test void disabledExtractionDoesNotStartOrCallAi() {
        create(new OpenAiProperties(false, "", "model", 5, 3_000_000, 2, 4000)).process(6L);
        assertEquals("UPLOADED", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void onlyNewlyUploadedTestsAreProcessed() {
        test.finishExtraction("Already", null);
        service.process(6L);
        verifyNoInteractions(client);
        assertEquals("NEEDS_REVIEW", test.getStatus());
    }
    @Test void hittingAttemptLimitStopsWithoutAi() {
        test.startProcessing(); test.startProcessing(); test.resetForRetry();
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void missingTestFailsBeforeAiCall() {
        when(tests.findById(6L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.process(6L));
        verifyNoInteractions(client);
    }
    @Test void invalidPdfIsMarkedError() {
        test = new BloodTest(7L, "bad.pdf", 8L, "sha", "not-a-pdf".getBytes());
        when(tests.findById(6L)).thenReturn(Optional.of(test));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void overlargePdfIsRejectedBeforeAi() {
        create(new OpenAiProperties(true, "fake", "model", 5, 10, 2, 4000)).process(6L);
        assertEquals("ERROR", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void tooManyPagesIsRejected() throws Exception {
        byte[] many = validPdf(2);
        test = new BloodTest(7L, "2pages.pdf", (long)many.length, "sha", many);
        when(tests.findById(6L)).thenReturn(Optional.of(test));
        create(new OpenAiProperties(true, "fake", "model", 1, 3_000_000, 2, 4000)).process(6L);
        assertEquals("ERROR", test.getStatus());
        verifyNoInteractions(client);
    }
    @Test void emptyPagesPdfIsRejected() throws Exception {
        byte[] empty = validPdf(0);
        test = new BloodTest(7L, "0pages.pdf", (long)empty.length, "sha", empty);
        when(tests.findById(6L)).thenReturn(Optional.of(test));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
    }
    @Test void aiExceptionCausesErrorState() {
        when(client.extract(any())).thenThrow(new IllegalStateException("AI failed"));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
        verify(indicators, never()).saveAll(any());
    }
    @Test void missingReportAndMissingIndicatorsCauseError() {
        when(client.extract(any())).thenReturn(null);
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
        test.resetForRetry();
        when(client.extract(any())).thenReturn(new ExtractedReport("lab", "2026-11-14", List.of()));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
    }
    @Test void blankNameOrMissingValueCausesError() {
        when(client.extract(any())).thenReturn(new ExtractedReport("lab", null,
                List.of(new ExtractedReport.Indicator(" ", "3", "u", "1-5"))));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
        test.resetForRetry();
        when(client.extract(any())).thenReturn(new ExtractedReport("lab", null,
                List.of(new ExtractedReport.Indicator("WBC", null, "u", "1-5"))));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
    }
    @Test void exceedingFieldLengthsCausesError() {
        when(client.extract(any())).thenReturn(new ExtractedReport("A".repeat(256), null,
                List.of(new ExtractedReport.Indicator("WBC", "8", null, null))));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
        test.resetForRetry();
        when(client.extract(any())).thenReturn(new ExtractedReport("lab", null,
                List.of(new ExtractedReport.Indicator("WBC", "8", "U".repeat(81), null))));
        service.process(6L);
        assertEquals("ERROR", test.getStatus());
    }
    @Test void wrongStateBeforeSavingDoesNotWriteIndicators() {
        when(client.extract(any())).thenAnswer(invocation -> {
            test.finishExtraction("changed meanwhile", null);
            return report("2026-11-14");
        });
        service.process(6L);
        verify(indicators, never()).saveAll(any());
    }
}
