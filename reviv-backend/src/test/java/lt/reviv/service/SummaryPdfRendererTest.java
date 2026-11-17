package lt.reviv.service;

import lt.reviv.bloodtests.service.IndicatorStatusCalculator;
import lt.reviv.bloodtests.service.summary.SummaryPdfRenderer;
import lt.reviv.bloodtests.service.summary.SummaryReport;
import lt.reviv.bloodtests.service.summary.SummaryReport.AnalysisNote;
import lt.reviv.bloodtests.service.summary.SummaryReport.IndicatorLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.Patient;
import lt.reviv.bloodtests.service.summary.SummaryReport.PlanLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.TestSection;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-14: the doctor summary PDF (layout, Lithuanian letters, tables, pagination, footer). */
class SummaryPdfRendererTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);

    private static IndicatorLine indicator(String name, String value, String unit, String reference) {
        return new IndicatorLine(name, value, unit, reference, IndicatorStatusCalculator.calculate(value, reference));
    }

    private static Patient patient() {
        return new Patient("Domantas Martišiūnas", "domantas@example.com", "29 m.", "182 cm", "78 kg",
                "Penicilinas, riešutai", "Nerūkau");
    }

    private static TestSection test(String file, LocalDate date, List<IndicatorLine> indicators) {
        return new TestSection(file, "Antakalnio poliklinika", date, indicators);
    }

    private static SummaryReport report(List<TestSection> tests, List<AnalysisNote> analyses, List<PlanLine> plan) {
        return new SummaryReport(DAY, "Visas laikotarpis", patient(), true, true, true, true, tests, analyses, plan);
    }

    private static SummaryReport sample() {
        return report(
                List.of(test("Antakpol_2025-04-30.pdf", LocalDate.of(2025, 4, 30), List.of(
                        indicator("Hemoglobinas (HGB)", "135", "g/l", "120 - 160"),
                        indicator("Didelių trombocitų kiekis (P-LCC)", "116", "10^9/l", "30 - 90"),
                        indicator("Vitaminas D (25-OH)", "18", "ng/ml", "30 - 100"),
                        indicator("Rodiklis be intervalo", "4.4", "mmol/l", "Nenurodyta")))),
                List.of(new AnalysisNote(LocalDate.of(2025, 4, 30), "Vitaminas D (25-OH)",
                        "Vitamino D koncentracija yra žemesnė už referencinį intervalą.")),
                List.of(new PlanLine("Vitaminas D3", "25 mcg", "2026-11-01 – 2027-01-30", 9, 1, 2, 75),
                        new PlanLine("Cinkas", "15 mg", "2026-11-17 – 2026-12-17", 0, 0, 0, null)));
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void TA72_pdfKeepsLithuanianLettersInsteadOfStrippingThem() throws IOException {
        byte[] pdf = SummaryPdfRenderer.render(sample());

        String text = text(pdf);
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF-"));
        assertTrue(text.contains("Gydytojo suvestinė"), text);
        assertTrue(text.contains("Martišiūnas"));
        assertTrue(text.contains("Didelių trombocitų kiekis"));
        assertTrue(text.contains("Aukščiau intervalo"));
        assertTrue(text.contains("Žemiau intervalo"));
    }

    @Test
    void TA72_pdfHasTheSectionsPatientAndKeyFigures() throws IOException {
        String text = text(SummaryPdfRenderer.render(sample()));

        for (String expected : new String[]{"Pacientas", "Tyrimai ir rodikliai", "DI paaiškinimai",
                "Papildų planas ir vartojimas", "Penicilinas, riešutai", "domantas@example.com", "182 cm",
                "Rodikliai už normos ribų", "Papildai plane", "Vartojimo nuoseklumas", "Visas laikotarpis",
                "2026-11-17", "SVARBU"}) {
            assertTrue(text.contains(expected), "Missing: " + expected);
        }
    }

    @Test
    void TA72_indicatorsShowTheirStatusAndTheNumberOfDeviations() throws IOException {
        String text = text(SummaryPdfRenderer.render(sample()));

        assertTrue(text.contains("Normos ribose"));
        assertTrue(text.contains("Nenustatyta"), "An indicator without an interval has no status");
        assertTrue(text.contains("2 už ribų"));
        assertTrue(text.contains("4 rodikl."));
    }

    @Test
    void TA72_planTableShowsDosesCyclesAndAdherence() throws IOException {
        String text = text(SummaryPdfRenderer.render(sample()));

        assertTrue(text.contains("Vitaminas D3"));
        assertTrue(text.contains("25 mcg"));
        assertTrue(text.contains("2026-11-01 – 2027-01-30"));
        assertTrue(text.contains("75 %"));
        assertTrue(text.contains("Cinkas"));
    }

    @Test
    void TA72_aiExplanationsAreMarkedWithTheAiBadge() throws IOException {
        String text = text(SummaryPdfRenderer.render(sample()));

        assertTrue(text.contains("DI\n") || text.contains("DI "), "The AI badge is missing");
        assertTrue(text.contains("Vitamino D koncentracija yra žemesnė"));
    }

    @Test
    void TA73_longTablesContinueOnTheNextPageWithRepeatedHeaderAndNote() throws IOException {
        List<IndicatorLine> many = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            many.add(indicator("Rodiklis " + i, String.format(Locale.US, "%.1f", 40 + i * 0.5), "mmol/l", "50 - 120"));
        }
        byte[] pdf = SummaryPdfRenderer.render(report(
                List.of(test("didelis.pdf", LocalDate.of(2025, 10, 2), many)), List.of(), List.of()));

        String text = text(pdf);
        assertTrue(pages(pdf) >= 4, "120 rows need several pages");
        assertTrue(text.contains("(tęsinys)"));
        assertTrue(text.contains("Rodiklis 120"));
        assertTrue(text.split("Ref. intervalas", -1).length - 1 >= 4, "The table header must repeat on each page");
    }

    @Test
    void TA73_everyPageHasAFooterWithItsPageNumber() throws IOException {
        List<IndicatorLine> many = new ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            many.add(indicator("Rodiklis " + i, "60", "mmol/l", "50 - 120"));
        }
        byte[] pdf = SummaryPdfRenderer.render(report(
                List.of(test("didelis.pdf", LocalDate.of(2025, 10, 2), many)), List.of(), List.of()));

        String text = text(pdf);
        int total = pages(pdf);
        for (int page = 1; page <= total; page++) {
            assertTrue(text.contains("Puslapis " + page + " / " + total), "No footer on page " + page);
        }
        assertTrue(text.split("Reviv · gydytojo suvestinė", -1).length - 1 >= total);
    }

    @Test
    void TA73_aShortPlanTableIsNotSplitAcrossPages() throws IOException {
        List<IndicatorLine> rows = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            rows.add(indicator("Rodiklis " + i, "60", "mmol/l", "50 - 120"));
        }
        byte[] pdf = SummaryPdfRenderer.render(report(
                List.of(test("t.pdf", LocalDate.of(2025, 10, 2), rows)), List.of(),
                List.of(new PlanLine("Vitaminas D3", "25 mcg", "2026-11-01 – 2027-01-30", 9, 1, 2, 75),
                        new PlanLine("Geležis", "14 mg", "2026-11-01 – 2026-12-30", 3, 6, 3, 25),
                        new PlanLine("Magnis", "200 mg", "2026-11-10 – 2027-01-08", 6, 0, 0, 100))));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pageWithHeading = -1;
            int pageWithLastRow = -1;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                if (pageText.contains("Papildų planas ir vartojimas")) pageWithHeading = page;
                if (pageText.contains("Magnis")) pageWithLastRow = page;
            }
            assertTrue(pageWithHeading > 0);
            assertEquals(pageWithHeading, pageWithLastRow, "The whole plan table must be on one page");
        }
    }

    @Test
    void TA74_emptyReportShowsFriendlyMessagesInsteadOfEmptyTables() throws IOException {
        String text = text(SummaryPdfRenderer.render(report(List.of(), List.of(), List.of())));

        assertTrue(text.contains("Nėra įtrauktų tyrimų pasirinktu laikotarpiu."));
        assertTrue(text.contains("Nėra DI paaiškinimų pasirinktame laikotarpyje."));
        assertTrue(text.contains("Nėra papildų vartojimo įrašų pasirinktu laikotarpiu."));
    }

    @Test
    void TA74_sectionsThatWereNotRequestedAreLeftOut() throws IOException {
        SummaryReport onlyPlan = new SummaryReport(DAY, "Paskutiniai 3 mėn.", patient(), false, false, true, false,
                sample().tests(), sample().analyses(), sample().plan());

        String text = text(SummaryPdfRenderer.render(onlyPlan));

        assertFalse(text.contains("Tyrimai ir rodikliai"));
        assertFalse(text.contains("DI paaiškinimai"));
        assertFalse(text.contains("Vartojimo nuoseklumas"), "No adherence figure without the history section");
        assertTrue(text.contains("Papildų planas ir vartojimas"));
        assertTrue(text.contains("Paskutiniai 3 mėn."));
        assertFalse(text.contains("Nuoseklumas\n"), "The adherence column is hidden without the history section");
        assertTrue(text.contains("Papildų planas\n") || text.contains("Papildų planas "), "The subtitle lists only the included parts");
    }

    @Test
    void TA74_missingValuesAreShownAsDashes() throws IOException {
        SummaryReport report = report(
                List.of(new TestSection("a.pdf", null, null, List.of(new IndicatorLine(
                        "Rodiklis", null, null, null, null)))),
                List.of(), List.of(new PlanLine("Papildas", null, null, 0, 0, 0, null)));

        byte[] pdf = SummaryPdfRenderer.render(report);

        String text = text(pdf);
        assertTrue(text.contains("Data nenurodyta"));
        assertTrue(text.contains("Nenustatyta"));
        assertTrue(text.contains("–"));
    }

    @Test
    void TA74_symbolsTheFontCannotDrawAreReplacedInsteadOfBreakingThePdf() throws IOException {
        SummaryReport report = report(
                List.of(test("t.pdf", DAY, List.of(indicator("Rodiklis 💊 su\tsimboliu\nir eilute", "5", "x", "1 - 9")))),
                List.of(), List.of());

        byte[] pdf = SummaryPdfRenderer.render(report);

        String text = text(pdf);
        assertTrue(text.contains("Rodiklis ? su simboliu ir eilute"), text);
    }

    @Test
    void TA74_veryLongWordsAndTextsAreWrappedWithinThePage() throws IOException {
        String longWord = "A".repeat(300);
        String longText = ("Ilgas paaiškinimas ").repeat(400);
        SummaryReport report = new SummaryReport(DAY, "Visas laikotarpis",
                new Patient(longWord, longWord + "@example.com", "29 m.", "182 cm", "78 kg", longText, longText),
                true, true, true, true,
                List.of(test("t.pdf", DAY, List.of(indicator(longWord, "5", "x", "1 - 9")))),
                List.of(new AnalysisNote(DAY, longWord, longText)),
                List.of(new PlanLine(longWord, "1 mg", "–", 1, 0, 0, 100)));

        byte[] pdf = SummaryPdfRenderer.render(report);

        assertTrue(pages(pdf) >= 1);
        assertNotNull(text(pdf));
    }

    @Test
    void TA74_documentMetadataHasATitleAndTheFontsAreEmbedded() throws IOException {
        byte[] pdf = SummaryPdfRenderer.render(sample());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals("Reviv gydytojo suvestinė 2026-11-17", document.getDocumentInformation().getTitle());
            assertEquals("Reviv", document.getDocumentInformation().getAuthor());
        }
        assertTrue(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1).contains("OpenSans"),
                "Open Sans must be embedded so that Lithuanian letters work everywhere");
    }
}
