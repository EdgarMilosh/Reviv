package lt.reviv.service;

import lt.reviv.bloodtests.service.analysis.IndicatorDeviationCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class IndicatorDeviationCalculatorTest {

    private IndicatorDeviationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new IndicatorDeviationCalculator();
    }

    @Test
    void shouldDetectBelowRange() {
        var result = calculator.calculate("18", "30-100");

        assertEquals(
                IndicatorDeviationCalculator.Status.BELOW,
                result.status()
        );
        assertEquals(
                new BigDecimal("40.0"),
                result.percentage()
        );
    }

    @Test
    void shouldDetectAboveRange() {
        var result = calculator.calculate("120", "30-100");

        assertEquals(
                IndicatorDeviationCalculator.Status.ABOVE,
                result.status()
        );
        assertEquals(
                new BigDecimal("20.0"),
                result.percentage()
        );
    }

    @Test
    void shouldDetectNormalRange() {
        var result = calculator.calculate("65", "30-100");

        assertEquals(
                IndicatorDeviationCalculator.Status.NORMAL,
                result.status()
        );
        assertEquals(
                BigDecimal.ZERO,
                result.percentage()
        );
    }

    @Test
    void shouldAcceptValuesOnInclusiveBoundaries() {
        var lower = calculator.calculate("30", "30-100");
        var upper = calculator.calculate("100", "30-100");

        assertEquals(
                IndicatorDeviationCalculator.Status.NORMAL,
                lower.status()
        );
        assertEquals(
                IndicatorDeviationCalculator.Status.NORMAL,
                upper.status()
        );
    }

    @Test
    void shouldSupportDecimalCommas() {
        var result = calculator.calculate("7,5", "10,0-20,0");

        assertEquals(
                IndicatorDeviationCalculator.Status.BELOW,
                result.status()
        );
        assertEquals(
                new BigDecimal("25.0"),
                result.percentage()
        );
    }

    @Test
    void shouldSupportOneSidedInterval() {
        var result = calculator.calculate("8", "<5");

        assertEquals(
                IndicatorDeviationCalculator.Status.ABOVE,
                result.status()
        );
        assertEquals(
                new BigDecimal("60.0"),
                result.percentage()
        );
    }

    @Test
    void shouldRejectInvalidValues() {
        var result = calculator.calculate("abc", "30-100");

        assertEquals(
                IndicatorDeviationCalculator.Status.UNKNOWN,
                result.status()
        );
        assertNull(result.percentage());
    }

    @Test
    void shouldRejectReversedRange() {
        var result = calculator.calculate("50", "100-30");

        assertEquals(
                IndicatorDeviationCalculator.Status.UNKNOWN,
                result.status()
        );
    }

    @Test
    void shouldHandleZeroBoundary() {
        var result = calculator.calculate("-2", "0-10");

        assertEquals(
                IndicatorDeviationCalculator.Status.BELOW,
                result.status()
        );
        assertNull(result.percentage());
    }
}
