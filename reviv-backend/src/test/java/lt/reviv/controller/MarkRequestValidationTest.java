package lt.reviv.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lt.reviv.doses.domain.DoseLog;
import lt.reviv.doses.dto.MarkRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-13: the mark request must contain a status. */
class MarkRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void TA50_requestWithoutStatusIsRejected() {
        Set<ConstraintViolation<MarkRequest>> violations = validator.validate(new MarkRequest(null));

        assertEquals(1, violations.size());
        assertEquals("status", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void TA50_takenAndSkippedAreAccepted() {
        assertTrue(validator.validate(new MarkRequest(DoseLog.Status.TAKEN)).isEmpty());
        assertTrue(validator.validate(new MarkRequest(DoseLog.Status.SKIPPED)).isEmpty());
    }
}