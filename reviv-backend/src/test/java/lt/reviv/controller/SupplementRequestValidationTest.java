package lt.reviv.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lt.reviv.supplements.domain.SupplementForm;
import lt.reviv.supplements.dto.DistributorRequest;
import lt.reviv.supplements.dto.SupplementRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-10: katalogo užklausos tikrinimas (privalomi laukai, dozės, nuorodos formatas). */
class SupplementRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<String> invalidFields(SupplementRequest request) {
        Set<ConstraintViolation<SupplementRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static SupplementRequest withDose(String dose, String maxDose) {
        return new SupplementRequest("Kalcis", "Kalcio citratas", SupplementForm.TABLET,
                new BigDecimal(dose), "mg", new BigDecimal(maxDose), null, null);
    }

    @Test
    void TA42_requestWithoutRequiredFieldsIsRejected() {
        SupplementRequest empty = new SupplementRequest("", "", null, null, "", null, null, null);

        assertEquals(Set.of("name", "activeSubstance", "form", "doseAmount", "doseUnit", "maxDailyDose"),
                invalidFields(empty));
    }

    @Test
    void TA42_zeroOrNegativeDoseIsRejected() {
        assertEquals(Set.of("doseAmount"), invalidFields(withDose("0", "50")));
        assertEquals(Set.of("doseAmount"), invalidFields(withDose("-5", "50")));
        assertEquals(Set.of("maxDailyDose"), invalidFields(withDose("10", "-1")));
    }

    @Test
    void TA55_invalidDistributorUrlIsRejected() {
        SupplementRequest request = new SupplementRequest("Kalcis", "Kalcio citratas", SupplementForm.TABLET,
                new BigDecimal("500"), "mg", new BigDecimal("2500"), null,
                List.of(new DistributorRequest("Blogas formatas", "ne nuoroda"),
                        new DistributorRequest("Kita schema", "ftp://pavyzdys.lt"),
                        new DistributorRequest("Gera", "https://pavyzdys.lt/kalcis")));

        assertEquals(Set.of("distributors[0].url", "distributors[1].url"), invalidFields(request));
    }

    @Test
    void TA57_validRequestIsAccepted() {
        SupplementRequest request = new SupplementRequest("Kalcis", "Kalcio citratas", SupplementForm.TABLET,
                new BigDecimal("500"), "mg", new BigDecimal("2500"), "Nedvigubinti dozės",
                List.of(new DistributorRequest("Demonstracinė parduotuvė", "https://example.com/kalcis")));

        assertTrue(invalidFields(request).isEmpty());
    }
}