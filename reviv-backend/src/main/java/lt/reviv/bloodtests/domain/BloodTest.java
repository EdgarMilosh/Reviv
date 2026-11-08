package lt.reviv.bloodtests.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    }
}
