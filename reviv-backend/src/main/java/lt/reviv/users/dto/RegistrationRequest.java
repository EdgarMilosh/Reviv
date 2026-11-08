package lt.reviv.users.dto;

import jakarta.validation.constraints.AssertTrue;

public record RegistrationRequest(
        @AssertTrue(message = "Consent to process health data is required")
        boolean consent
) {}