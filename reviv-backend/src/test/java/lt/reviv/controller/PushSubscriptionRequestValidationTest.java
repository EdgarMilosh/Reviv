package lt.reviv.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lt.reviv.push.dto.PushSubscriptionRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-12: validation of the subscription sent by the browser. */
class PushSubscriptionRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<String> invalidFields(PushSubscriptionRequest request) {
        Set<ConstraintViolation<PushSubscriptionRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void TA63_httpsEndpointWithKeysIsAccepted() {
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/abc", new PushSubscriptionRequest.Keys("p256dh", "auth"));

        assertTrue(invalidFields(request).isEmpty());
    }

    @Test
    void TA63_nonHttpsOrBlankEndpointIsRejected() {
        PushSubscriptionRequest.Keys keys = new PushSubscriptionRequest.Keys("p256dh", "auth");

        assertEquals(Set.of("endpoint"), invalidFields(new PushSubscriptionRequest("http://push.example.com/a", keys)));
        assertEquals(Set.of("endpoint"), invalidFields(new PushSubscriptionRequest("", keys)));
    }

    @Test
    void TA63_missingOrBlankKeysAreRejected() {
        assertEquals(Set.of("keys"),
                invalidFields(new PushSubscriptionRequest("https://push.example.com/a", null)));
        assertEquals(Set.of("keys.p256dh", "keys.auth"),
                invalidFields(new PushSubscriptionRequest("https://push.example.com/a",
                        new PushSubscriptionRequest.Keys("", " "))));
    }
}