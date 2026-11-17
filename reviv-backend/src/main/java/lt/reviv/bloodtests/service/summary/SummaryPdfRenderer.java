package lt.reviv.bloodtests.service.summary;

import lt.reviv.bloodtests.service.IndicatorStatusCalculator.Status;
import lt.reviv.bloodtests.service.summary.SummaryReport.AnalysisNote;
import lt.reviv.bloodtests.service.summary.SummaryReport.IndicatorLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.PlanLine;
import lt.reviv.bloodtests.service.summary.SummaryReport.TestSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the doctor summary PDF (A4): a coloured header, a patient card, key figures, indicator tables with
 * status badges, AI explanations, the supplement plan with adherence bars, and a footer with page numbers.
 * Fonts are embedded (Open Sans), so Lithuanian letters are kept as they are.
 */
public final class SummaryPdfRenderer {

    /** A colour as red/green/blue fractions; avoids java.awt, so the renderer also works on a bare server JRE. */
    private record Rgb(float r, float g, float b) {
    }

    private static Rgb rgb(int red, int green, int blue) {
        return new Rgb(red / 255f, green / 255f, blue / 255f);
    }

    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float MARGIN = 42f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
    private static final float TOP = PAGE_H - 46f;
    private static final float BOTTOM = 64f;
    private static final float HEADER_H = 98f;

    private static final Rgb TEAL = rgb(0x0F, 0x76, 0x6E);
    private static final Rgb TEAL_DARK = rgb(0x0B, 0x4F, 0x4A);
    private static final Rgb TEAL_TINT = rgb(0xE6, 0xF4, 0xF1);
    private static final Rgb INK = rgb(0x1F, 0x29, 0x37);
    private static final Rgb MUTED = rgb(0x64, 0x74, 0x8B);
    private static final Rgb LINE = rgb(0xE2, 0xE8, 0xF0);
    private static final Rgb RULE = rgb(0xCB, 0xD5, 0xE1);
    private static final Rgb SOFT = rgb(0xF8, 0xFA, 0xFC);
    private static final Rgb PANEL = rgb(0xF1, 0xF5, 0xF9);
    private static final Rgb WHITE = rgb(0xFF, 0xFF, 0xFF);

    private static final Rgb OK_TEXT = rgb(0x04, 0x78, 0x57);
    private static final Rgb OK_FILL = rgb(0xD1, 0xFA, 0xE5);
    private static final Rgb LOW_TEXT = rgb(0x1D, 0x4E, 0xD8);
    private static final Rgb LOW_FILL = rgb(0xDB, 0xEA, 0xFE);
    private static final Rgb LOW_ROW = rgb(0xF3, 0xF8, 0xFF);
    private static final Rgb HIGH_TEXT = rgb(0xC2, 0x41, 0x0C);
    private static final Rgb HIGH_FILL = rgb(0xFF, 0xED, 0xD5);
    private static final Rgb HIGH_ROW = rgb(0xFF, 0xF8, 0xF1);
    private static final Rgb NA_TEXT = rgb(0x47, 0x55, 0x69);
    private static final Rgb NA_FILL = rgb(0xE2, 0xE8, 0xF0);

    private final PDDocument document;
    private final PDFont regular;
    private final PDFont semibold;
    private final PDFont bold;
    private final Map<PDFont, Map<Integer, Boolean>> glyphs = new IdentityHashMap<>();
    private final SummaryReport report;

    private PDPageContentStream out;
    private float y;

    private SummaryPdfRenderer(PDDocument document, SummaryReport report) throws IOException {
        this.document = document;
        this.report = report;
        this.regular = loadFont(document, "OpenSans-Regular.ttf");
        this.semibold = loadFont(document, "OpenSans-SemiBold.ttf");
        this.bold = loadFont(document, "OpenSans-Bold.ttf");
    }

    /** Renders the report and returns the PDF bytes. */
    public static byte[] render(SummaryReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            SummaryPdfRenderer renderer = new SummaryPdfRenderer(document, report);
            renderer.build();
            document.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nepavyko sugeneruoti suvestinės PDF.", e);
        }
    }

    private static PDFont loadFont(PDDocument document, String name) throws IOException {
        try (InputStream in = SummaryPdfRenderer.class.getResourceAsStream("/fonts/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Trūksta šrifto failo: /fonts/" + name);
            }
            return PDType0Font.load(document, in);
        }
    }

    // ------------------------------------------------------------------ document

    private void build() throws IOException {
        setMetadata();
        newPage();
        drawHeader();
        y = PAGE_H - HEADER_H - 26f;

        drawPatient();
        drawKeyFigures();

        if (report.includeTests()) {
            drawTests();
        }
        if (report.includeAnalyses()) {
            drawAnalyses();
        }
        if (report.includePlan() || report.includeHistory()) {
            drawPlan();
        }
        drawDisclaimer();

        out.close();
        out = null;
        drawFooters();
    }

    private void setMetadata() {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle("Reviv gydytojo suvestinė " + report.generatedOn());
        info.setAuthor("Reviv");
        info.setSubject("Kraujo tyrimų, papildų plano ir vartojimo suvestinė gydytojui");
        info.setCreator("Reviv");
        info.setCreationDate(Calendar.getInstance());
    }

    // ------------------------------------------------------------------ header and patient

    private void drawHeader() throws IOException {
        fillRect(0, PAGE_H - HEADER_H, PAGE_W, HEADER_H, TEAL);
        fillRect(0, PAGE_H - HEADER_H, PAGE_W, 3f, TEAL_DARK);

        spaced("REVIV", MARGIN, PAGE_H - 31f, bold, 8.5f, rgb(0xA7, 0xE3, 0xDA), 2.4f);
        text("Gydytojo suvestinė", MARGIN, PAGE_H - 56f, bold, 23f, WHITE);
        text(subtitle(), MARGIN, PAGE_H - 75f, regular, 9.3f, rgb(0xD1, 0xEF, 0xE9));

        float right = PAGE_W - MARGIN;
        textRight("SUGENERUOTA", right, PAGE_H - 31f, bold, 7f, rgb(0xA7, 0xE3, 0xDA));
        textRight(String.valueOf(report.generatedOn()), right, PAGE_H - 45f, semibold, 10.5f, WHITE);
        textRight("LAIKOTARPIS", right, PAGE_H - 62f, bold, 7f, rgb(0xA7, 0xE3, 0xDA));
        textRight(report.periodLabel(), right, PAGE_H - 76f, semibold, 10.5f, WHITE);
    }

    /** Lists only the parts that are really in this summary. */
    private String subtitle() {
        List<String> parts = new ArrayList<>();
        if (report.includeTests() || report.includeAnalyses()) {
            parts.add("kraujo tyrimai");
        }
        if (report.includeAnalyses()) {
            parts.add("DI paaiškinimai");
        }
        if (report.includePlan() || report.includeHistory()) {
            parts.add("papildų planas");
        }
        if (report.includeHistory()) {
            parts.add("vartojimo istorija");
        }
        if (parts.isEmpty()) {
            return "Naudotojo profilis";
        }
        String joined = String.join(", ", parts);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }

    private void drawPatient() throws IOException {
        SummaryReport.Patient p = report.patient();
        float pad = 15f;
        float nameW = 150f;
        float emailW = 214f;
        float ageW = CONTENT_W - 2 * pad - nameW - emailW - 2 * 16f;
        List<String> allergies = wrap(p.allergies(), semibold, 10f, CONTENT_W - 2 * pad);
        List<String> habits = wrap(p.habits(), semibold, 10f, CONTENT_W - 2 * pad);
        float height = pad + 34f + 34f + (13f + allergies.size() * 13f + 9f) + (13f + habits.size() * 13f + 9f) + 2f;

        sectionHeading("Pacientas");
        ensure(height + 10f);
        float top = y;
        fillStrokeRound(MARGIN, top - height, CONTENT_W, height, 8f, WHITE, LINE, 0.9f);

        float x1 = MARGIN + pad;
        float x2 = x1 + nameW + 16f;
        float x3 = x2 + emailW + 16f;
        float row1 = top - pad;
        field("VARDAS", ellipsize(p.name(), semibold, 10f, nameW), x1, row1);
        field("EL. PAŠTAS", ellipsize(p.email(), semibold, 10f, emailW), x2, row1);
        field("AMŽIUS", ellipsize(p.age(), semibold, 10f, ageW), x3, row1);
        float row2 = row1 - 34f;
        field("ŪGIS", p.height(), x1, row2);
        field("SVORIS", p.weight(), x2, row2);

        float row3 = row2 - 34f;
        text("ALERGIJOS", x1, row3 - 8f, bold, 6.8f, MUTED);
        float baseline = row3 - 21f;
        for (String line : allergies) {
            text(line, x1, baseline, semibold, 10f, INK);
            baseline -= 13f;
        }
        float row4 = row3 - (13f + allergies.size() * 13f + 9f);
        text("ŽALINGI ĮPROČIAI", x1, row4 - 8f, bold, 6.8f, MUTED);
        baseline = row4 - 21f;
        for (String line : habits) {
            text(line, x1, baseline, semibold, 10f, INK);
            baseline -= 13f;
        }
        y = top - height - 16f;
    }

    private void field(String label, String value, float x, float top) throws IOException {
        text(label, x, top - 8f, bold, 6.8f, MUTED);
        text(value, x, top - 21f, semibold, 10f, INK);
    }

    private void drawKeyFigures() throws IOException {
        List<String[]> tiles = new ArrayList<>();
        List<Boolean> alert = new ArrayList<>();
        if (report.includeTests() || report.includeAnalyses()) {
            tiles.add(new String[]{String.valueOf(report.tests().size()), "Tyrimai"});
            alert.add(false);
            long deviations = report.totalOutOfRange();
            tiles.add(new String[]{String.valueOf(deviations), "Rodikliai už normos ribų"});
            alert.add(deviations > 0);
        }
        if (report.includePlan() || report.includeHistory()) {
            tiles.add(new String[]{String.valueOf(report.plan().size()), "Papildai plane"});
            alert.add(false);
        }
        if (report.includeHistory()) {
            Integer adherence = report.overallAdherence();
            tiles.add(new String[]{adherence == null ? "–" : adherence + " %", "Vartojimo nuoseklumas"});
            alert.add(adherence != null && adherence < 50);
        }
        if (tiles.isEmpty()) {
            return;
        }
        float gap = 10f;
        float w = (CONTENT_W - gap * (tiles.size() - 1)) / tiles.size();
        float h = 54f;
        ensure(h + 12f);
        float top = y;
        for (int i = 0; i < tiles.size(); i++) {
            float x = MARGIN + i * (w + gap);
            fillStrokeRound(x, top - h, w, h, 8f, SOFT, LINE, 0.9f);
            text(tiles.get(i)[0], x + 14f, top - 28f, bold, 20f, alert.get(i) ? HIGH_TEXT : TEAL);
            text(ellipsize(tiles.get(i)[1], regular, 8.3f, w - 24f), x + 14f, top - 43f, regular, 8.3f, MUTED);
        }
        y = top - h - 8f;
    }

    // ------------------------------------------------------------------ tests

    private void drawTests() throws IOException {
        sectionHeading("Tyrimai ir rodikliai");
        if (report.tests().isEmpty()) {
            note("Nėra įtrauktų tyrimų pasirinktu laikotarpiu.");
            return;
        }
        float[] widths = {172f, 72f, 62f, 92f, 113f};
        String[] headers = {"Rodiklis", "Reikšmė", "Vienetai", "Ref. intervalas", "Būsena"};
        for (TestSection test : report.tests()) {
            ensure(34f + 22f + 24f);
            drawTestHeader(test);
            if (test.indicators().isEmpty()) {
                note("Šiam tyrimui rodiklių nėra.");
                y -= 6f;
                continue;
            }
            drawTableHead(headers, widths, true);
            int index = 0;
            for (IndicatorLine line : test.indicators()) {
                float rowH = Math.max(22f, wrap(line.name(), regular, 9f, widths[0] - 16f).size() * 11.6f + 10f);
                if (y - rowH < BOTTOM) {
                    newPage();
                    text(testTitle(test) + " (tęsinys)", MARGIN, y - 9f, semibold, 8.2f, MUTED);
                    y -= 16f;
                    drawTableHead(headers, widths, true);
                }
                drawIndicatorRow(line, widths, rowH, index++);
            }
            y -= 16f;
        }
    }

    private void drawTestHeader(TestSection test) throws IOException {
        float h = 36f;
        float top = y;
        fillRound(MARGIN, top - h, CONTENT_W, h, 7f, PANEL);
        String title = testTitle(test).replace(" · ", "   ·   ");
        float right = MARGIN + CONTENT_W - 12f;

        long deviations = test.outOfRange();
        float chipRight = right;
        if (deviations > 0) {
            String label = deviations + " už ribų";
            float w = width(semibold, 7.8f, label) + 14f;
            pill(label, chipRight - w, top - 12f - 7f, w, 14f, HIGH_TEXT, HIGH_FILL, 7.8f);
            chipRight -= w + 6f;
        }
        String count = test.indicators().size() + " rodikl.";
        float cw = width(semibold, 7.8f, count) + 14f;
        pill(count, chipRight - cw, top - 12f - 7f, cw, 14f, NA_TEXT, WHITE, 7.8f);
        chipRight -= cw + 8f;

        text(ellipsize(title, bold, 10.4f, chipRight - MARGIN - 24f), MARGIN + 12f, top - 15f, bold, 10.4f, INK);
        text(ellipsize(test.fileName(), regular, 7.8f, chipRight - MARGIN - 24f), MARGIN + 12f, top - 28f,
                regular, 7.8f, MUTED);
        y = top - h - 6f;
    }

    private static String testTitle(TestSection test) {
        return (test.date() == null ? "Data nenurodyta" : test.date().toString())
                + (blank(test.laboratory()) ? "" : " · " + test.laboratory());
    }

    private void drawTableHead(String[] headers, float[] widths, boolean rightSecond) throws IOException {
        float x = MARGIN;
        for (int i = 0; i < headers.length; i++) {
            if (rightSecond && i == 1) {
                textRight(headers[i], x + widths[i] - 10f, y - 13f, semibold, 7.6f, MUTED);
            } else {
                text(headers[i], x + 8f, y - 13f, semibold, 7.6f, MUTED);
            }
            x += widths[i];
        }
        strokeLine(MARGIN, y - 20f, MARGIN + CONTENT_W, y - 20f, RULE, 0.8f);
        y -= 21f;
    }

    private void drawIndicatorRow(IndicatorLine line, float[] widths, float rowH, int index) throws IOException {
        Status status = line.status() == null ? Status.UNDETERMINED : line.status();
        Rgb background = status == Status.ABOVE_RANGE ? HIGH_ROW
                : status == Status.BELOW_RANGE ? LOW_ROW
                : index % 2 == 1 ? SOFT : WHITE;
        fillRect(MARGIN, y - rowH, CONTENT_W, rowH, background);

        float x = MARGIN;
        List<String> nameLines = wrap(line.name(), regular, 9f, widths[0] - 16f);
        float pad = (rowH - nameLines.size() * 11.6f) / 2f;
        float baseline = y - pad - 8.6f;
        for (String nameLine : nameLines) {
            text(nameLine, x + 8f, baseline, regular, 9f, INK);
            baseline -= 11.6f;
        }
        x += widths[0];

        float mid = y - rowH / 2f - 3.2f;
        boolean abnormal = status == Status.ABOVE_RANGE || status == Status.BELOW_RANGE;
        Rgb valueColor = status == Status.ABOVE_RANGE ? HIGH_TEXT : status == Status.BELOW_RANGE ? LOW_TEXT : INK;
        textRight(orDash(line.value()), x + widths[1] - 10f, mid, abnormal ? bold : semibold, 9.4f, valueColor);
        x += widths[1];
        text(ellipsize(orDash(line.unit()), regular, 8.6f, widths[2] - 10f), x + 8f, mid, regular, 8.6f, MUTED);
        x += widths[2];
        text(ellipsize(orDash(line.reference()), regular, 8.6f, widths[3] - 10f), x + 8f, mid, regular, 8.6f, MUTED);
        x += widths[3];

        String label;
        Rgb fg;
        Rgb bg;
        switch (status) {
            case BELOW_RANGE -> { label = "Žemiau intervalo"; fg = LOW_TEXT; bg = LOW_FILL; }
            case ABOVE_RANGE -> { label = "Aukščiau intervalo"; fg = HIGH_TEXT; bg = HIGH_FILL; }
            case WITHIN_RANGE -> { label = "Normos ribose"; fg = OK_TEXT; bg = OK_FILL; }
            default -> { label = "Nenustatyta"; fg = NA_TEXT; bg = NA_FILL; }
        }
        float w = width(semibold, 7.6f, label) + 14f;
        pill(label, x + 6f, y - rowH / 2f - 7f, w, 14f, fg, bg, 7.6f);
        strokeLine(MARGIN, y - rowH, MARGIN + CONTENT_W, y - rowH, LINE, 0.5f);
        y -= rowH;
    }

    // ------------------------------------------------------------------ AI explanations

    private void drawAnalyses() throws IOException {
        sectionHeading("DI paaiškinimai");
        if (report.analyses().isEmpty()) {
            note("Nėra DI paaiškinimų pasirinktame laikotarpyje.");
            return;
        }
        float textW = CONTENT_W - 36f;
        float lineH = 12.6f;
        int maxLines = (int) ((TOP - BOTTOM - 70f) / lineH);
        for (AnalysisNote analysis : report.analyses()) {
            List<String> lines = wrap(analysis.meaning(), regular, 9.2f, textW);
            if (lines.size() > maxLines) {
                lines = new ArrayList<>(lines.subList(0, maxLines));
                lines.set(maxLines - 1, ellipsize(lines.get(maxLines - 1) + " …", regular, 9.2f, textW));
            }
            float h = 16f + 17f + lines.size() * lineH + 8f;
            ensure(h + 8f);
            float top = y;
            fillStrokeRound(MARGIN, top - h, CONTENT_W, h, 7f, WHITE, LINE, 0.9f);
            fillRect(MARGIN + 0.5f, top - h + 7f, 3.2f, h - 14f, TEAL);

            float x = MARGIN + 18f;
            pill("DI", x, top - 12f - 7f, 22f, 14f, TEAL, TEAL_TINT, 7.4f);
            String date = analysis.testDate() == null ? "" : analysis.testDate().toString();
            float dateW = date.isEmpty() ? 0f : width(regular, 8f, date);
            text(ellipsize(analysis.indicatorName(), bold, 9.8f, CONTENT_W - 36f - 30f - dateW - 16f),
                    x + 30f, top - 16.6f, bold, 9.8f, INK);
            if (!date.isEmpty()) {
                textRight(date, MARGIN + CONTENT_W - 14f, top - 16.6f, regular, 8f, MUTED);
            }
            float baseline = top - 16f - 17f;
            for (String line : lines) {
                text(line, x, baseline, regular, 9.2f, INK);
                baseline -= lineH;
            }
            y = top - h - 8f;
        }
    }

    // ------------------------------------------------------------------ plan

    private void drawPlan() throws IOException {
        boolean history = report.includeHistory();
        float[] widths = history ? new float[]{128f, 64f, 108f, 40f, 44f, 44f, 83f}
                : new float[]{200f, 120f, 191f};
        String[] headers = history
                ? new String[]{"Papildas", "Dozė", "Ciklas", "Išgerta", "Praleista", "Nepažym.", "Nuoseklumas"}
                : new String[]{"Papildas", "Dozė", "Ciklas"};
        // A short table stays on one page: if it does not fit below, the whole section starts on a new page.
        float total = 40f + 21f + 12f;
        for (PlanLine line : report.plan()) {
            total += Math.max(28f, wrap(line.supplement(), semibold, 9.2f, widths[0] - 16f).size() * 11.6f + 14f);
        }
        if (!report.plan().isEmpty() && total > y - BOTTOM && total <= TOP - BOTTOM) {
            newPage();
        }
        sectionHeading("Papildų planas ir vartojimas");
        if (report.plan().isEmpty()) {
            note("Nėra papildų vartojimo įrašų pasirinktu laikotarpiu.");
            return;
        }
        ensure(21f + 28f);
        drawTableHead(headers, widths, false);
        int index = 0;
        for (PlanLine line : report.plan()) {
            float rowH = Math.max(28f, wrap(line.supplement(), semibold, 9.2f, widths[0] - 16f).size() * 11.6f + 14f);
            if (y - rowH < BOTTOM) {
                newPage();
                drawTableHead(headers, widths, false);
            }
            drawPlanRow(line, widths, rowH, index++, history);
        }
        y -= 12f;
    }

    private void drawPlanRow(PlanLine line, float[] widths, float rowH, int index, boolean history) throws IOException {
        fillRect(MARGIN, y - rowH, CONTENT_W, rowH, index % 2 == 1 ? SOFT : WHITE);
        float mid = y - rowH / 2f - 3.2f;
        float x = MARGIN;
        List<String> names = wrap(line.supplement(), semibold, 9.2f, widths[0] - 16f);
        float baseline = y - (rowH - names.size() * 11.6f) / 2f - 8.7f;
        for (String name : names) {
            text(name, x + 8f, baseline, semibold, 9.2f, INK);
            baseline -= 11.6f;
        }
        x += widths[0];
        text(ellipsize(orDash(line.dose()), regular, 8.8f, widths[1] - 10f), x + 8f, mid, regular, 8.8f, INK);
        x += widths[1];
        text(ellipsize(orDash(line.cycle()), regular, 8.4f, widths[2] - 10f), x + 8f, mid, regular, 8.4f, MUTED);
        x += widths[2];
        if (history) {
            textRight(String.valueOf(line.taken()), x + widths[3] - 8f, mid, semibold, 9f, OK_TEXT);
            x += widths[3];
            textRight(String.valueOf(line.skipped()), x + widths[4] - 8f, mid, semibold, 9f,
                    line.skipped() > 0 ? HIGH_TEXT : MUTED);
            x += widths[4];
            textRight(String.valueOf(line.unmarked()), x + widths[5] - 8f, mid, semibold, 9f, MUTED);
            x += widths[5];
            drawAdherence(line.adherencePercent(), x + 10f, y - rowH / 2f, widths[6] - 18f);
        }
        strokeLine(MARGIN, y - rowH, MARGIN + CONTENT_W, y - rowH, LINE, 0.5f);
        y -= rowH;
    }

    private void drawAdherence(Integer percent, float x, float centerY, float width) throws IOException {
        if (percent == null) {
            text("–", x, centerY - 3f, regular, 9f, MUTED);
            return;
        }
        Rgb color = percent >= 80 ? OK_TEXT : percent >= 50 ? rgb(0xB4, 0x53, 0x09) : HIGH_TEXT;
        String label = percent + " %";
        float labelW = width(bold, 8.6f, label);
        float barW = width - labelW - 6f;
        fillRound(x, centerY - 3f, barW, 6f, 3f, LINE);
        float filled = Math.max(0f, Math.min(1f, percent / 100f)) * barW;
        if (filled > 0.5f) {
            fillRound(x, centerY - 3f, Math.max(filled, 3f), 6f, 3f, color);
        }
        text(label, x + barW + 6f, centerY - 3f, bold, 8.6f, color);
    }

    // ------------------------------------------------------------------ closing parts

    private void drawDisclaimer() throws IOException {
        String body = "Ši suvestinė sudaryta iš naudotojo įkeltų tyrimų ir nėra diagnozė ar gydymo rekomendacija. "
                + (report.includeAnalyses()
                ? "DI paaiškinimai pažymėti ženklu „DI“ ir yra informacinio pobūdžio. " : "")
                + "Sprendimus dėl papildų ir gydymo priima gydytojas.";
        List<String> lines = wrap(body, regular, 8.8f, CONTENT_W - 32f);
        float h = 14f + 14f + lines.size() * 12f + 8f;
        ensure(h + 14f);
        y -= 10f;
        float top = y;
        fillStrokeRound(MARGIN, top - h, CONTENT_W, h, 7f, rgb(0xFF, 0xF8, 0xE1), rgb(0xF2, 0xD9, 0x8B), 0.9f);
        text("SVARBU", MARGIN + 16f, top - 17f, bold, 7.4f, rgb(0x7C, 0x5A, 0x00));
        float baseline = top - 31f;
        for (String line : lines) {
            text(line, MARGIN + 16f, baseline, regular, 8.8f, rgb(0x5B, 0x43, 0x00));
            baseline -= 12f;
        }
        y = top - h;
    }

    private void drawFooters() throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream footer = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                out = footer;
                strokeLine(MARGIN, 46f, PAGE_W - MARGIN, 46f, RULE, 0.8f);
                text("Reviv · gydytojo suvestinė", MARGIN, 31f, regular, 8f, MUTED);
                float centerText = width(regular, 8f, "Suvestinė nėra diagnozė");
                text("Suvestinė nėra diagnozė", (PAGE_W - centerText) / 2f, 31f, regular, 8f, MUTED);
                textRight("Puslapis " + (i + 1) + " / " + total, PAGE_W - MARGIN, 31f, semibold, 8f, MUTED);
            }
        }
        out = null;
    }

    // ------------------------------------------------------------------ building blocks

    private void sectionHeading(String title) throws IOException {
        ensure(64f);
        y -= 6f;
        text(title, MARGIN, y - 13f, bold, 13f, TEAL_DARK);
        strokeLine(MARGIN, y - 21f, MARGIN + CONTENT_W, y - 21f, RULE, 0.8f);
        fillRect(MARGIN, y - 22f, 38f, 2.2f, TEAL);
        y -= 34f;
    }

    private void note(String message) throws IOException {
        ensure(24f);
        text(message, MARGIN, y - 12f, regular, 9.4f, MUTED);
        y -= 24f;
    }

    private void pill(String label, float x, float bottom, float w, float h, Rgb fg, Rgb bg, float size)
            throws IOException {
        fillRound(x, bottom, w, h, h / 2f, bg);
        text(label, x + (w - width(semibold, size, label)) / 2f, bottom + (h - size) / 2f + size * 0.2f,
                semibold, size, fg);
    }

    private void newPage() throws IOException {
        if (out != null) {
            out.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        out = new PDPageContentStream(document, page);
        y = TOP;
    }

    private void ensure(float height) throws IOException {
        if (y - height < BOTTOM) {
            newPage();
        }
    }

    private void text(String value, float x, float baseline, PDFont font, float size, Rgb color) throws IOException {
        String cleaned = clean(font, value);
        if (cleaned.isEmpty()) {
            return;
        }
        out.beginText();
        out.setFont(font, size);
        fillColor(color);
        out.newLineAtOffset(x, baseline);
        out.showText(cleaned);
        out.endText();
    }

    private void spaced(String value, float x, float baseline, PDFont font, float size, Rgb color, float spacing)
            throws IOException {
        out.beginText();
        out.setFont(font, size);
        fillColor(color);
        out.setCharacterSpacing(spacing);
        out.newLineAtOffset(x, baseline);
        out.showText(clean(font, value));
        out.setCharacterSpacing(0f);
        out.endText();
    }

    private void textRight(String value, float right, float baseline, PDFont font, float size, Rgb color)
            throws IOException {
        text(value, right - width(font, size, value), baseline, font, size, color);
    }

    private float width(PDFont font, float size, String value) throws IOException {
        String cleaned = clean(font, value);
        return cleaned.isEmpty() ? 0f : font.getStringWidth(cleaned) / 1000f * size;
    }

    private void fillColor(Rgb color) throws IOException {
        out.setNonStrokingColor(color.r(), color.g(), color.b());
    }

    private void strokeColor(Rgb color) throws IOException {
        out.setStrokingColor(color.r(), color.g(), color.b());
    }

    private void fillRect(float x, float bottom, float w, float h, Rgb color) throws IOException {
        fillColor(color);
        out.addRect(x, bottom, w, h);
        out.fill();
    }

    private void strokeLine(float x1, float y1, float x2, float y2, Rgb color, float lineWidth) throws IOException {
        strokeColor(color);
        out.setLineWidth(lineWidth);
        out.moveTo(x1, y1);
        out.lineTo(x2, y2);
        out.stroke();
    }

    private void fillRound(float x, float bottom, float w, float h, float r, Rgb fill) throws IOException {
        roundPath(x, bottom, w, h, Math.min(r, Math.min(w, h) / 2f));
        fillColor(fill);
        out.fill();
    }

    private void fillStrokeRound(float x, float bottom, float w, float h, float r, Rgb fill, Rgb stroke,
                                 float lineWidth) throws IOException {
        roundPath(x, bottom, w, h, Math.min(r, Math.min(w, h) / 2f));
        fillColor(fill);
        strokeColor(stroke);
        out.setLineWidth(lineWidth);
        out.fillAndStroke();
    }

    private void roundPath(float x, float y0, float w, float h, float r) throws IOException {
        float k = 0.5522847498f * r;
        out.moveTo(x + r, y0);
        out.lineTo(x + w - r, y0);
        out.curveTo(x + w - r + k, y0, x + w, y0 + r - k, x + w, y0 + r);
        out.lineTo(x + w, y0 + h - r);
        out.curveTo(x + w, y0 + h - r + k, x + w - r + k, y0 + h, x + w - r, y0 + h);
        out.lineTo(x + r, y0 + h);
        out.curveTo(x + r - k, y0 + h, x, y0 + h - r + k, x, y0 + h - r);
        out.lineTo(x, y0 + r);
        out.curveTo(x, y0 + r - k, x + r - k, y0, x + r, y0);
        out.closePath();
    }

    // ------------------------------------------------------------------ text helpers

    /** Replaces control characters and characters the font cannot draw, so a stray symbol never breaks the PDF. */
    private String clean(PDFont font, String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                codePoint = ' ';
            }
            if (codePoint < 32) {
                continue;
            }
            builder.appendCodePoint(supports(font, codePoint) ? codePoint : '?');
        }
        return builder.toString();
    }

    private boolean supports(PDFont font, int codePoint) {
        return glyphs.computeIfAbsent(font, f -> new java.util.HashMap<>()).computeIfAbsent(codePoint, cp -> {
            try {
                font.getStringWidth(new String(Character.toChars(cp)));
                return true;
            } catch (IOException | IllegalArgumentException e) {
                return false;
            }
        });
    }

    private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String cleaned = clean(font, value).trim();
        if (cleaned.isEmpty()) {
            lines.add("–");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : cleaned.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(font, size, candidate) <= maxWidth) {
                line = new StringBuilder(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            String rest = word;
            while (width(font, size, rest) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && width(font, size, rest.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line = new StringBuilder(rest);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String ellipsize(String value, PDFont font, float size, float maxWidth) throws IOException {
        String cleaned = clean(font, value).trim();
        if (cleaned.isEmpty()) {
            return "–";
        }
        if (width(font, size, cleaned) <= maxWidth) {
            return cleaned;
        }
        String cut = cleaned;
        while (cut.length() > 1 && width(font, size, cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.trim() + "…";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String orDash(String value) {
        return blank(value) ? "–" : value.trim();
    }
}
