package lt.reviv.config;

import lt.reviv.push.config.PushProperties;
import lt.reviv.push.config.ReminderProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-12: VAPID and reminder settings (DP-3: configuration comes from the environment). */
class PushConfigurationTest {

    @Test
    void TA59_pushIsNotConfiguredWithoutKeys() {
        PushProperties properties = new PushProperties(null, null, null);

        assertFalse(properties.configured());
        assertEquals("", properties.publicKey());
        assertEquals("mailto:admin@reviv.local", properties.subject());
    }

    @Test
    void TA59_pushIsConfiguredWhenBothKeysArePresent() {
        PushProperties properties = new PushProperties(" public ", " private ", " mailto:me@test.lt ");

        assertTrue(properties.configured());
        assertEquals("public", properties.publicKey());
        assertEquals("mailto:me@test.lt", properties.subject());
        assertFalse(new PushProperties("public", "", "mailto:me@test.lt").configured());
    }

    @Test
    void TA59_reminderDefaultsApplyWhenSettingsAreMissingOrInvalid() {
        ReminderProperties properties = new ReminderProperties(null, " ", 0, 99);

        assertTrue(properties.enabled());
        assertEquals("Europe/Vilnius", properties.zone());
        assertEquals(10, properties.catchUpMinutes());
        assertEquals(9, properties.cycleNoticeHour());
    }

    @Test
    void TA59_reminderKeepsValidSettings() {
        ReminderProperties properties = new ReminderProperties(false, " UTC ", 5, 7);

        assertFalse(properties.enabled());
        assertEquals("UTC", properties.zone());
        assertEquals(5, properties.catchUpMinutes());
        assertEquals(7, properties.cycleNoticeHour());
    }
}