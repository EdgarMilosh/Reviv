package lt.reviv.service;

import lt.reviv.bloodtests.service.IndicatorStatusCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static lt.reviv.bloodtests.service.IndicatorStatusCalculator.Status.*;

class IndicatorStatusCalculatorTest {

    @Test
    void shouldDetectValuesOutsideNormalRange() {
        assertEquals(BELOW_RANGE,
                IndicatorStatusCalculator.calculate("18", "30-100"));

        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("50", "30-100"));

        assertEquals(ABOVE_RANGE,
                IndicatorStatusCalculator.calculate("110", "30-100"));
    }

    @Test
    void shouldIncludeIntervalBoundaries() {
        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("30", "30-100"));

        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("100", "30-100"));

        assertEquals(BELOW_RANGE,
                IndicatorStatusCalculator.calculate("29", "30-100"));

        assertEquals(ABOVE_RANGE,
                IndicatorStatusCalculator.calculate("101", "30-100"));
    }

    @Test
    void shouldSupportDecimalValues() {
        assertEquals(BELOW_RANGE,
                IndicatorStatusCalculator.calculate("0,342", "0,36–0,42"));

        assertEquals(ABOVE_RANGE,
                IndicatorStatusCalculator.calculate("20,5", "11,6–13,7"));

        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("3,5", "3,5–5,5"));
    }

    @Test
    void shouldSupportLessThanIntervals() {
        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("6", "<20"));

        assertEquals(ABOVE_RANGE,
                IndicatorStatusCalculator.calculate("20", "<20"));

        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("20", "<=20"));

        assertEquals(ABOVE_RANGE,
                IndicatorStatusCalculator.calculate("21", "<=20"));

        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("20", "≤20"));
    }

    @Test
    void shouldSupportGreaterThanIntervals() {
        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("31", ">30"));

        assertEquals(BELOW_RANGE,
                IndicatorStatusCalculator.calculate("30", ">30"));

        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("30", ">=30"));

        assertEquals(BELOW_RANGE,
                IndicatorStatusCalculator.calculate("29", ">=30"));

        assertEquals(WITHIN_RANGE,
                IndicatorStatusCalculator.calculate("30", "≥30"));
    }

    @Test
    void shouldHandleInvalidIntervals() {
        assertEquals(UNDETERMINED,
                IndicatorStatusCalculator.calculate("15", "abc"));

        assertEquals(UNDETERMINED,
                IndicatorStatusCalculator.calculate("15", "100-30"));

        assertEquals(UNDETERMINED,
                IndicatorStatusCalculator.calculate("15", ""));

        assertEquals(UNDETERMINED,
                IndicatorStatusCalculator.calculate("abc", "30-100"));
    }

    @Test
    void shouldHandleMissingValues() {
        assertEquals(UNDETERMINED,
                IndicatorStatusCalculator.calculate(null, "30-100"));

        assertEquals(UNDETERMINED,
                IndicatorStatusCalculator.calculate("50", null));
    }
}
