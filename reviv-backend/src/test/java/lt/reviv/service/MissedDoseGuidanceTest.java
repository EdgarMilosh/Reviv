package lt.reviv.service;

import lt.reviv.doses.service.MissedDoseGuidance;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-13: what to do after a missed dose. */
class MissedDoseGuidanceTest {

    private static final LocalTime EIGHT = LocalTime.of(8, 0);

    private static MissedDoseGuidance.Advice at(int hour, int minute) {
        return MissedDoseGuidance.advise(LocalDateTime.of(2026, 11, 16, hour, minute), EIGHT);
    }

    @Test
    void TA66_drinkNowWhenMoreThanHalfOfTheIntervalIsLeft() {
        MissedDoseGuidance.Advice advice = at(10, 0);

        assertEquals(MissedDoseGuidance.Action.DRINK_NOW, advice.action());
        assertTrue(advice.message().contains("išgerti dabar"));
        assertTrue(advice.message().contains("nedvigubinkite"));
    }

    @Test
    void TA66_skipWhenLessThanHalfOfTheIntervalIsLeft() {
        MissedDoseGuidance.Advice advice = at(21, 0);

        assertEquals(MissedDoseGuidance.Action.SKIP, advice.action());
        assertTrue(advice.message().contains("negerkite dvigubos dozės"));
    }

    @Test
    void TA66_exactlyHalfOfTheIntervalMeansSkip() {
        assertEquals(MissedDoseGuidance.Action.SKIP, at(20, 0).action());
        assertEquals(MissedDoseGuidance.Action.DRINK_NOW, at(19, 59).action());
    }

    @Test
    void TA66_markedBeforeTheScheduledTimeStillAllowsDrinkingNow() {
        assertEquals(MissedDoseGuidance.Action.DRINK_NOW, at(7, 0).action());
    }

    @Test
    void TA66_theSameRuleAppliesToOtherDoseTimes() {
        LocalTime evening = LocalTime.of(19, 0);
        // Evening dose marked at 20:00: 23 hours are left until tomorrow's dose.
        assertEquals(MissedDoseGuidance.Action.DRINK_NOW,
                MissedDoseGuidance.advise(LocalDateTime.of(2026, 11, 16, 20, 0), evening).action());
        // A 06:00 dose marked at 19:00: only 11 hours are left until tomorrow's dose.
        assertEquals(MissedDoseGuidance.Action.SKIP,
                MissedDoseGuidance.advise(LocalDateTime.of(2026, 11, 16, 19, 0), LocalTime.of(6, 0)).action());
    }
}