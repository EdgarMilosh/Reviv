package lt.reviv.domain;

import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static lt.reviv.bloodtests.domain.rec.SupplementRecommendation.Decision.*;
import static lt.reviv.bloodtests.domain.rec.SupplementRecommendation.RecommendationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplementRecommendationTest {
    private SupplementRecommendation suggested() {
        return new SupplementRecommendation(1L, 16L, 5L,
                "Geležis", SUGGESTED, new BigDecimal("14"), "mg", 30,
                "Rodiklis žemiau intervalo", null, true);
    }

    @Test
    void newSuggestionIsPendingAndHasTimestampsAndSnapshots() {
        var item = suggested();
        assertEquals(1L, item.getGenerationId());
        assertEquals(16L, item.getIndicatorId());
        assertEquals(5L, item.getSupplementId());
        assertEquals("Geležis", item.getSupplementNameSnapshot());
        assertEquals(SUGGESTED, item.getRecommendationStatus());
        assertEquals(PENDING, item.getDecision());
        assertEquals(new BigDecimal("14"), item.getProposedDailyDose());
        assertEquals("mg", item.getDoseUnit());
        assertEquals(30, item.getDurationDays());
        assertEquals("Rodiklis žemiau intervalo", item.getReason());
        assertEquals("[]", item.getRiskWarningsJson());
        assertTrue(item.isNeedsSpecialistReview());
        assertNotNull(item.getCreatedAt());
        assertNull(item.getDecidedAt());
        assertNull(item.getId());
    }

    @Test
    void canPersistNotRecommendedWithoutSupplementOrDose() {
        var item = new SupplementRecommendation(1L, 17L, null, null,
                NOT_RECOMMENDED, null, null, null, "Netinka", "[\"Alergija\"]", false);
        assertEquals(NOT_RECOMMENDED, item.getRecommendationStatus());
        assertEquals("[\"Alergija\"]", item.getRiskWarningsJson());
    }

    @Test
    void canPersistNeedsReviewWithoutSupplementOrDose() {
        var item = new SupplementRecommendation(1L, 17L, null, null,
                NEEDS_REVIEW, null, null, null, "Neaišku", "[]", true);
        assertTrue(item.isNeedsSpecialistReview());
    }

    @Test
    void nullRecommendationStatusRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SupplementRecommendation(1L, 17L, null, null,
                        null, null, null, null, "Priežastis", "[]", false));
    }

    @Test
    void notRecommendedMustNotHaveSupplementOrDoseOrDuration() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SupplementRecommendation(1L, 17L, 5L, "Geležis",
                                NOT_RECOMMENDED, null, null, null, "Priežastis", "[]", false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SupplementRecommendation(1L, 17L, null, null,
                                NEEDS_REVIEW, BigDecimal.ONE, null, null, "Priežastis", "[]", false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SupplementRecommendation(1L, 17L, null, null,
                                NOT_RECOMMENDED, null, "mg", null, "Priežastis", "[]", false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SupplementRecommendation(1L, 17L, null, null,
                                NEEDS_REVIEW, null, null, 30, "Priežastis", "[]", false))
        );
    }

    @Test
    void reasonMustBePresent() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SupplementRecommendation(1L, 1L, null, null,
                                NOT_RECOMMENDED, null, null, null, null, "[]", false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SupplementRecommendation(1L, 1L, null, null,
                                NOT_RECOMMENDED, null, null, null, "  ", "[]", false))
        );
    }

    @Test
    void acceptedRecommendationCannotBeChangedAgain() {
        var item = suggested();
        item.decide(ACCEPTED);
        assertEquals(ACCEPTED, item.getDecision());
        assertNotNull(item.getDecidedAt());
        assertThrows(IllegalStateException.class, () -> item.decide(REJECTED));
    }

    @Test
    void rejectedRecommendationCannotBeAcceptedLater() {
        var item = suggested();
        item.decide(REJECTED);
        assertEquals(REJECTED, item.getDecision());
        assertThrows(IllegalStateException.class, () -> item.decide(ACCEPTED));
    }

    @Test
    void notRecommendedCannotBeAccepted() {
        var item = new SupplementRecommendation(1L, 17L, null, null,
                NOT_RECOMMENDED, null, null, null, "Netinka", "[]", false);
        assertThrows(IllegalStateException.class, () -> item.decide(ACCEPTED));
    }

    @Test
    void pendingAndNullAreNotUserDecisions() {
        assertThrows(IllegalArgumentException.class, () -> suggested().decide(PENDING));
        assertThrows(IllegalArgumentException.class, () -> suggested().decide(null));
    }
}
