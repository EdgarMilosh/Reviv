package lt.reviv.supplements.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lt.reviv.supplements.domain.SupplementForm;

import java.math.BigDecimal;
import java.util.List;

public record SupplementRequest(
        @NotBlank(message = "Įveskite pavadinimą")
        @Size(max = 120, message = "Pavadinimas negali būti ilgesnis nei 120 simbolių")
        String name,

        @NotBlank(message = "Įveskite veikliąją medžiagą")
        @Size(max = 120, message = "Veiklioji medžiaga negali būti ilgesnė nei 120 simbolių")
        String activeSubstance,

        @NotNull(message = "Pasirinkite formą")
        SupplementForm form,

        @NotNull(message = "Įveskite kiekį vienoje dozėje")
        @DecimalMin(value = "0", inclusive = false, message = "Dozė turi būti didesnė už nulį")
        @Digits(integer = 8, fraction = 4, message = "Neteisinga reikšmė (iki 4 skaitmenų po kablelio)")
        BigDecimal doseAmount,

        @NotBlank(message = "Įveskite matavimo vienetą")
        @Size(max = 20, message = "Matavimo vienetas negali būti ilgesnis nei 20 simbolių")
        String doseUnit,

        @NotNull(message = "Įveskite maksimalią saugią paros dozę")
        @DecimalMin(value = "0", inclusive = false, message = "Maksimali paros dozė turi būti didesnė už nulį")
        @Digits(integer = 8, fraction = 4, message = "Neteisinga reikšmė (iki 4 skaitmenų po kablelio)")
        BigDecimal maxDailyDose,

        @Size(max = 500, message = "Taisyklė negali būti ilgesnė nei 500 simbolių")
        String missedDoseRule,

        @Valid
        List<DistributorRequest> distributors
) {
}