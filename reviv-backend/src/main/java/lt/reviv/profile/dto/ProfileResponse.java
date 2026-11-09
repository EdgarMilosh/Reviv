package lt.reviv.profile.dto;

import lt.reviv.profile.domain.Profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ProfileResponse(
        Integer heightCm,
        LocalDate birthDate,
        BigDecimal weightKg,
        String[] harmfulHabits,
        String[] allergies,
        OffsetDateTime updatedAt
) {
    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getHeightCm(),
                profile.getBirthDate(),
                profile.getWeightKg(),
                profile.getHarmfulHabits(),
                profile.getAllergies(),
                profile.getUpdatedAt()
        );
    }
}
