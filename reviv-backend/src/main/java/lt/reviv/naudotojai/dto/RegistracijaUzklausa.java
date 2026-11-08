package lt.reviv.naudotojai.dto;

import jakarta.validation.constraints.AssertTrue;

public record RegistracijaUzklausa(
        @AssertTrue(message = "Būtinas sutikimas tvarkyti sveikatos duomenis")
        boolean sutikimas
) {}