package lt.reviv.profile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfileRequest(
        @NotNull(message = "Height is required")
        @Min(value = 50, message = "Height must be between 50 and 300 cm")
        @Max(value = 300, message = "Height must be between 50 and 300 cm")
        Integer heightCm,

        LocalDate birthDate,

        @NotNull(message = "Weight is required")
        @DecimalMin(value = "20.00", message = "Weight must be between 20 and 300 kg")
        @DecimalMax(value = "300.00", message = "Weight must be between 20 and 300 kg")
        BigDecimal weightKg,

        String[] harmfulHabits,
        String[] allergies
) {
}
