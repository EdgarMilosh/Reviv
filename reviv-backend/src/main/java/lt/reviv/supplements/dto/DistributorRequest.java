package lt.reviv.supplements.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DistributorRequest(
        @NotBlank(message = "Įveskite platintojo pavadinimą")
        @Size(max = 120, message = "Pavadinimas negali būti ilgesnis nei 120 simbolių")
        String name,

        @NotBlank(message = "Įveskite nuorodą į parduotuvę")
        @Size(max = 500, message = "Nuoroda negali būti ilgesnė nei 500 simbolių")
        @Pattern(regexp = "^https?://[^\\s/$.?#][^\\s]*$",
                message = "Neteisingas nuorodos formatas (pvz., https://pavyzdys.lt)")
        String url
) {
}