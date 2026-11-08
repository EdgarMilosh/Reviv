package lt.reviv.bloodtests.dto;

import lt.reviv.bloodtests.domain.BloodTest;
import java.time.OffsetDateTime;

public record BloodTestUploadResponse(
        Long id,
        String fileName,
        Long fileSize,
        String status,
        OffsetDateTime uploadedAt
) {
    public static BloodTestUploadResponse from(BloodTest test) {
        return new BloodTestUploadResponse(
                test.getId(),
                test.getFileName(),
                test.getFileSize(),
                test.getStatus(),
                test.getUploadedAt()
        );
    }
}
