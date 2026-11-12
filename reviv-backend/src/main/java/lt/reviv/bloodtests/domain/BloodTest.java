package lt.reviv.bloodtests.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "blood_test")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BloodTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "pdf_data", nullable = false, columnDefinition = "bytea")
    private byte[] pdfData;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "laboratory", length = 255)
    private String laboratory;

    @Column(name = "test_date")
    private LocalDate testDate;

    @Column(name = "extraction_error", length = 255)
    private String extractionError;

    @Column(name = "extraction_attempts", nullable = false)
    private int extractionAttempts;

    public BloodTest(
            Long userId,
            String fileName,
            Long fileSize,
            String fileSha256,
            byte[] pdfData
    ) {
        this.userId = userId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileSha256 = fileSha256;
        this.pdfData = pdfData.clone();
        this.status = "UPLOADED";
        this.uploadedAt = OffsetDateTime.now();
        this.extractionAttempts = 0;
    }

    public byte[] getPdfData() {
        return pdfData.clone();
    }

    public void startProcessing() {
        this.status = "PROCESSING";
        this.extractionAttempts++;
        this.extractionError = null;
    }

    public void finishExtraction(
            String laboratory,
            LocalDate testDate
    ) {
        this.laboratory = laboratory;
        this.testDate = testDate;
        this.status = "NEEDS_REVIEW";
        this.extractionError = null;
    }

    public void failExtraction() {
        this.status = "ERROR";
        this.extractionError =
                "Nepavyko ištraukti tyrimo duomenų.";
    }

    public void resetForRetry() {
        this.status = "UPLOADED";
        this.extractionError = null;
    }

    public void queueCorrection() {
        this.status = "RETRY_QUEUED";
    }

    public void confirm() {
        if (!"NEEDS_REVIEW".equals(this.status)) {
            throw new IllegalStateException(
                    "Tyrimas dar neparuoštas patvirtinimui."
            );
        }

        this.status = "CONFIRMED";
    }

}
