package lt.reviv.bloodtests.dto.review;

import lt.reviv.bloodtests.domain.BloodTest;

import java.time.OffsetDateTime;

public record BloodTestListResponse(
        Long id,
        String fileName,
        String laboratory,
        String status,
        OffsetDateTime uploadedAt
) {
    public static BloodTestListResponse from(BloodTest test) {
        return new BloodTestListResponse(
                test.getId(),
                test.getFileName(),
                test.getLaboratory(),
                test.getStatus(),
                test.getUploadedAt()
        );
    }
}
