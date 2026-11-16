package lt.reviv.config;

import lt.reviv.doses.config.AdherenceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** US-13: the "plan was not followed" threshold (default 50 %). */
class AdherencePropertiesTest {

    @Test
    void TA51_thresholdDefaultsToFiftyPercentWhenMissingOrInvalid() {
        assertEquals(50, new AdherenceProperties(null).missedThresholdPercent());
        assertEquals(50, new AdherenceProperties(0).missedThresholdPercent());
        assertEquals(50, new AdherenceProperties(100).missedThresholdPercent());
    }

    @Test
    void TA51_validThresholdIsKept() {
        assertEquals(30, new AdherenceProperties(30).missedThresholdPercent());
        assertEquals(99, new AdherenceProperties(99).missedThresholdPercent());
    }
}