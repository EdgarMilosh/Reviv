package lt.reviv.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lt.reviv.users.dto.RegistrationRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-1 / NFR-16: paskyra kuriama tik gavus aiškų sutikimą tvarkyti sveikatos duomenis. */
class RegistrationRequestTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void requestWithoutConsentIsRejected() {   // TA-03
        Set<ConstraintViolation<RegistrationRequest>> violations =
                validator.validate(new RegistrationRequest(false));

        assertEquals(1, violations.size());
        assertEquals("consent", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void requestWithConsentIsAccepted() {
        assertTrue(validator.validate(new RegistrationRequest(true)).isEmpty());
    }
}