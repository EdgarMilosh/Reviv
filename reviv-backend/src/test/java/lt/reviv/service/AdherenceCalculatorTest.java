package lt.reviv.service;

import lt.reviv.doses.service.AdherenceCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-13: planned doses, adherence percentage and the "plan was not followed" rule. */
class AdherenceCalculatorTest {

    private static LocalDate d(int day) {
        return LocalDate.of(2026, 11, day);
    }

    @Test
    void TA67_plannedDosesCountEveryDayOfTheCycleUpToTheLastJudgedDay() {
        assertEquals(10, AdherenceCalculator.plannedDoses(d(1), d(10), d(1), d(30), d(30)));
        assertEquals(5, AdherenceCalculator.plannedDoses(d(1), d(10), d(1), d(30), d(5)));
    }

    @Test
    void TA67_plannedDosesAreLimitedToTheRequestedPeriod() {
        assertEquals(4, AdherenceCalculator.plannedDoses(d(1), d(30), d(10), d(13), d(30)));
        assertEquals(3, AdherenceCalculator.plannedDoses(d(5), d(30), d(1), d(7), d(30)));
    }

    @Test
    void TA67_noDosesOutsideTheCycleOrInTheFuture() {
        assertEquals(0, AdherenceCalculator.plannedDoses(d(10), d(20), d(1), d(5), d(30)));
        assertEquals(0, AdherenceCalculator.plannedDoses(d(10), d(20), d(1), d(30), d(9)));
        assertEquals(0, AdherenceCalculator.plannedDoses(d(10), d(20), d(25), d(30), d(30)));
    }

    @Test
    void TA67_singleDayCycleHasOneDose() {
        assertEquals(1, AdherenceCalculator.plannedDoses(d(7), d(7), d(1), d(30), d(30)));
    }

    @Test
    void TA67_percentIsRoundedAndMissingWhenNothingWasPlanned() {
        assertEquals(100, AdherenceCalculator.percent(10, 10));
        assertEquals(67, AdherenceCalculator.percent(2, 3));
        assertEquals(0, AdherenceCalculator.percent(0, 5));
        assertNull(AdherenceCalculator.percent(0, 0));
    }

    @Test
    void TA51_planIsNotFollowedWhenMoreThanHalfOfTheDosesWereMissed() {
        assertTrue(AdherenceCalculator.notFollowed(10, 4, 50));
        assertFalse(AdherenceCalculator.notFollowed(10, 5, 50), "Exactly 50 % missed is not more than 50 %");
        assertFalse(AdherenceCalculator.notFollowed(10, 8, 50));
        assertFalse(AdherenceCalculator.notFollowed(0, 0, 50));
    }

    @Test
    void TA51_theThresholdIsConfigurable() {
        assertTrue(AdherenceCalculator.notFollowed(10, 7, 20));
        assertFalse(AdherenceCalculator.notFollowed(10, 7, 40));
    }
}