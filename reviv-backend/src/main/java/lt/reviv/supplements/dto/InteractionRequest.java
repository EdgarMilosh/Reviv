package lt.reviv.supplements.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InteractionRequest(
        @NotNull(message = "Pasirinkite papildą")
        Long otherSupplementId,

        @NotBlank(message = "Įveskite sąveikos tipą")
        @Size(max = 100, message = "Tipas negali būti ilgesnis nei 100 simbolių")
        String type,

        @NotBlank(message = "Įveskite rekomendaciją")
        @Size(max = 500, message = "Rekomendacija negali būti ilgesnė nei 500 simbolių")
        String recommendation
) {
}