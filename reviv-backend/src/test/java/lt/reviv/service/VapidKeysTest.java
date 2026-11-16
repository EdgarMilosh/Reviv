package lt.reviv.service;

import lt.reviv.push.config.VapidKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-12: the VAPID key holder. */
class VapidKeysTest {

    @Test
    void TA65_notConfiguredWithoutBothKeys() {
        assertFalse(new VapidKeys(null, null, null).configured());
        assertFalse(new VapidKeys("public", "", "mailto:t@test.lt").configured());
        assertFalse(new VapidKeys("", "private", "mailto:t@test.lt").configured());
    }

    @Test
    void TA65_configuredWithBothKeysAndTrimmed() {
        VapidKeys keys = new VapidKeys(" public ", " private ", " mailto:t@test.lt ");

        assertTrue(keys.configured());
        assertEquals("public", keys.publicKey());
        assertEquals("private", keys.privateKey());
        assertEquals("mailto:t@test.lt", keys.subject());
    }

    @Test
    void TA65_subjectHasADefault() {
        assertEquals("mailto:admin@reviv.local", new VapidKeys("a", "b", " ").subject());
    }
}