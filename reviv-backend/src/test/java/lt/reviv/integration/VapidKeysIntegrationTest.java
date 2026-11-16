package lt.reviv.integration;

import lt.reviv.common.settings.AppSettingRepository;
import lt.reviv.push.config.VapidKeys;
import lt.reviv.push.service.VapidKeyStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-12: with no VAPID keys configured, the application generates them on start and stores them. */
class VapidKeysIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VapidKeys keys;

    @Autowired
    private AppSettingRepository settings;

    @Autowired
    private VapidKeyStore store;

    @Test
    void TA65_applicationGeneratesAndStoresVapidKeysWhenNoneAreConfigured() {
        assertTrue(keys.configured());
        assertEquals(87, keys.publicKey().length());
        assertEquals(43, keys.privateKey().length());
        assertEquals(keys.publicKey(), settings.findById(VapidKeyStore.PUBLIC_KEY).orElseThrow().getValue());
        assertEquals(keys.privateKey(), settings.findById(VapidKeyStore.PRIVATE_KEY).orElseThrow().getValue());
    }

    @Test
    void TA65_storedKeysAreReusedInsteadOfGeneratingNewOnes() {
        VapidKeys again = store.loadOrCreate("mailto:another@test.lt");

        assertEquals(keys.publicKey(), again.publicKey());
        assertEquals(keys.privateKey(), again.privateKey());
    }
}