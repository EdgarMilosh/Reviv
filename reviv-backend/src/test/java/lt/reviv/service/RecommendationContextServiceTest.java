package lt.reviv.service;

import lt.reviv.bloodtests.dto.review.BloodTestDetailResponse;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.service.IndicatorStatusCalculator;
import lt.reviv.bloodtests.service.analysis.CriticalIndicatorService;
import lt.reviv.bloodtests.service.rec.RecommendationContextService;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;
import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.supplements.domain.Supplement;
import lt.reviv.supplements.domain.SupplementForm;
import lt.reviv.supplements.domain.SupplementInteraction;
import lt.reviv.supplements.repository.SupplementRepository;
import lt.reviv.supplements.repository.SupplementInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** US-9: prepare only confirmed, below-range indicators and active catalog entries. */
@ExtendWith(MockitoExtension.class)
class RecommendationContextServiceTest {
    @Mock BloodTestReviewService reviews;
    @Mock BloodTestRepository tests;
    @Mock SupplementRepository supplements;
    @Mock ProfileRepository profiles;
    @Mock CriticalIndicatorService critical;
    @Mock SupplementInteractionRepository interactions;
    RecommendationContextService service;
    Jwt jwt;

    @BeforeEach
    void init() {
        service = new RecommendationContextService(reviews, tests, supplements, profiles, critical, interactions);
        jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user").build();
    }

    private BloodTestDetailResponse.Indicator indicator(Long id, String name,
                                                       IndicatorStatusCalculator.Status status) {
        return new BloodTestDetailResponse.Indicator(id, name, "100", "g/L", "120-155", status);
    }

    private void detail(String status, List<BloodTestDetailResponse.Indicator> indicators) {
        when(reviews.getDetails(10L, jwt)).thenReturn(new BloodTestDetailResponse(
                10L, "demo.pdf", status, null, "Laboratorija", LocalDate.of(2026, 11, 10),
                null, indicators, List.of()));
    }

    private void owner(long userId) {
        var bloodTest = mock(BloodTest.class);
        when(bloodTest.getUserId()).thenReturn(userId);
        when(tests.findById(10L)).thenReturn(Optional.of(bloodTest));
    }

    private Supplement supplement(long id, String name) {
        var s = new Supplement(name, name, SupplementForm.CAPSULE,
                new BigDecimal("10"), "mg", new BigDecimal("40"), null);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    void unconfirmedTestIsRejectedBeforeDbCatalogIsRead() {
        detail("NEEDS_REVIEW", List.of());
        var error = assertThrows(ResponseStatusException.class, () -> service.prepare(10L, jwt));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(tests, supplements, profiles, critical, interactions);
    }

    @Test
    void missingTestIsReportedAfterReviewValidation() {
        detail("CONFIRMED", List.of());
        when(tests.findById(10L)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.prepare(10L, jwt));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void onlyBelowRangeGoesToAiAndOtherStatusesAreCounted() {
        detail("CONFIRMED", List.of(
                indicator(1L, "HGB", IndicatorStatusCalculator.Status.BELOW_RANGE),
                indicator(2L, "ALT", IndicatorStatusCalculator.Status.ABOVE_RANGE),
                indicator(3L, "D", IndicatorStatusCalculator.Status.WITHIN_RANGE),
                indicator(4L, "?", IndicatorStatusCalculator.Status.UNDETERMINED)));
        owner(77L);
        when(critical.assess("HGB", "100", "g/L"))
                .thenReturn(CriticalIndicatorService.CriticalStatus.NOT_ASSESSED);
        when(critical.assess("ALT", "100", "g/L"))
                .thenReturn(CriticalIndicatorService.CriticalStatus.NOT_CRITICAL_BY_RULE);

        var result = service.prepare(10L, jwt);
        assertEquals(1, result.indicators().size());
        assertEquals("HGB", result.indicators().getFirst().name());
        assertEquals(1, result.normalCount());
        assertEquals(1, result.undeterminedCount());
        assertTrue(result.criticalityNotAssessed());
        assertFalse(result.criticalDetected());
    }

    @Test
    void criticalIndicatorClearsAllCandidates() {
        detail("CONFIRMED", List.of(
                indicator(1L, "HGB", IndicatorStatusCalculator.Status.BELOW_RANGE),
                indicator(2L, "D", IndicatorStatusCalculator.Status.BELOW_RANGE)));
        owner(77L);
        when(critical.assess("HGB", "100", "g/L"))
                .thenReturn(CriticalIndicatorService.CriticalStatus.CRITICAL);
        when(critical.assess("D", "100", "g/L"))
                .thenReturn(CriticalIndicatorService.CriticalStatus.NOT_CRITICAL_BY_RULE);
        var result = service.prepare(10L, jwt);
        assertTrue(result.criticalDetected());
        assertTrue(result.indicators().isEmpty());
    }

    @Test
    void inactiveSupplementsAreOmittedAndInteractionsNeedBothActiveEnds() {
        detail("CONFIRMED", List.of());
        owner(77L);
        var activeA = supplement(1L, "Cinkas");
        var activeB = supplement(2L, "Geležis");
        var inactive = supplement(3L, "Senas papildas");
        inactive.markInactive();
        when(supplements.findAllByOrderByNameAsc()).thenReturn(List.of(activeA, activeB, inactive));
        when(interactions.findAll()).thenReturn(List.of(
                new SupplementInteraction(1L, 2L, "Mažina pasisavinimą", "Atskirkite"),
                new SupplementInteraction(1L, 3L, "Sąveika", "Netaikyti")));

        var context = service.prepare(10L, jwt);
        assertEquals(2, context.catalog().size());
        assertEquals(1, context.interactions().size());
        assertEquals(1L, context.interactions().getFirst().supplementAId());
        assertEquals(2L, context.interactions().getFirst().supplementBId());
        assertNull(context.profile().age());
        assertTrue(context.profile().allergies().isEmpty());
    }

    @Test
    void profileAgeAllergiesAndHabitsArePassedToOllama() {
        detail("CONFIRMED", List.of());
        owner(77L);
        var profile = mock(Profile.class);
        when(profile.getBirthDate()).thenReturn(LocalDate.now().minusYears(25));
        when(profile.getAllergies()).thenReturn(new String[]{"laktozė"});
        when(profile.getHarmfulHabits()).thenReturn(new String[]{"rūkymas"});
        when(profiles.findByUserId(77L)).thenReturn(Optional.of(profile));

        var result = service.prepare(10L, jwt);
        assertEquals(25, result.profile().age());
        assertEquals(List.of("laktozė"), result.profile().allergies());
        assertEquals(List.of("rūkymas"), result.profile().harmfulHabits());
    }

    @Test
    void futureBirthDateDoesNotProduceNegativeAge() {
        detail("CONFIRMED", List.of());
        owner(77L);
        var profile = mock(Profile.class);
        when(profile.getBirthDate()).thenReturn(LocalDate.now().plusYears(3));
        when(profile.getAllergies()).thenReturn(new String[0]);
        when(profile.getHarmfulHabits()).thenReturn(new String[0]);
        when(profiles.findByUserId(77L)).thenReturn(Optional.of(profile));

        var result = service.prepare(10L, jwt);
        assertNull(result.profile().age());
    }
}
