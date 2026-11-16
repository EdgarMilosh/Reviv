package lt.reviv.service;

import lt.reviv.push.config.PushProperties;
import lt.reviv.push.config.VapidKeys;
import lt.reviv.push.config.VapidKeysConfig;
import lt.reviv.push.service.VapidKeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-12: keys from the environment win; otherwise the stored or generated keys are used. */
@ExtendWith(MockitoExtension.class)
class VapidKeysConfigTest {

    @Mock
    private VapidKeyStore store;

    private final VapidKeysConfig config = new VapidKeysConfig();

    @Test
    void TA65_environmentKeysAreUsedWhenConfigured() {
        PushProperties properties = new PushProperties("env-public", "env-private", "mailto:env@test.lt");

        VapidKeys keys = config.vapidKeys(properties, store);

        assertEquals("env-public", keys.publicKey());
        assertEquals("env-private", keys.privateKey());
        assertEquals("mailto:env@test.lt", keys.subject());
        verifyNoInteractions(store);
    }

    @Test
    void TA65_storedOrGeneratedKeysAreUsedWithoutEnvironmentKeys() {
        PushProperties properties = new PushProperties("", "", "mailto:env@test.lt");
        VapidKeys stored = new VapidKeys("db-public", "db-private", "mailto:env@test.lt");
        when(store.loadOrCreate(anyString())).thenReturn(stored);

        VapidKeys keys = config.vapidKeys(properties, store);

        assertEquals(stored, keys);
        verify(store).loadOrCreate("mailto:env@test.lt");
    }
}