package lt.reviv.bloodtests.ai;

import java.util.List;

public interface PdfExtractionClient {

    ExtractedReport extract(byte[] pdfData);

    List<ExtractedReport.Correction> correct(
            byte[] pdfData,
            List<ExtractedReport.CorrectionTarget> targets
    );
}
