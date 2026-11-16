package lt.reviv.service;

import lt.reviv.common.settings.AppSetting;
import lt.reviv.common.settings.AppSettingRepository;
import lt.reviv.push.config.VapidKeys;
import lt.reviv.push.service.VapidKeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-12: generated VAPID keys are stored once and reused after a restart. */
@ExtendWith(MockitoExtension.class)
class VapidKeyStoreTest {

    @Mock
    private AppSettingRepository settings;

    @InjectMocks
    private VapidKeyStore store;

    @Test
    void TA65_generatesAndSavesKeysOnFirstUse() {
        when(settings.findById(VapidKeyStore.PUBLIC_KEY)).thenReturn(Optional.empty());
        when(settings.findById(VapidKeyStore.PRIVATE_KEY)).thenReturn(Optional.empty());

        VapidKeys keys = store.loadOrCreate("mailto:t@test.lt");

        assertTrue(keys.configured());
        ArgumentCaptor<AppSetting> saved = ArgumentCaptor.forClass(AppSetting.class);
        verify(settings, times(2)).save(saved.capture());
        List<AppSetting> values = saved.getAllValues();
        assertEquals(VapidKeyStore.PUBLIC_KEY, values.get(0).getKey());
        assertEquals(keys.publicKey(), values.get(0).getValue());
        assertEquals(VapidKeyStore.PRIVATE_KEY, values.get(1).getKey());
        assertEquals(keys.privateKey(), values.get(1).getValue());
    }

    @Test
    void TA65_reusesStoredKeysAfterRestart() {
        when(settings.findById(VapidKeyStore.PUBLIC_KEY))
                .thenReturn(Optional.of(new AppSetting(VapidKeyStore.PUBLIC_KEY, "stored-public")));
        when(settings.findById(VapidKeyStore.PRIVATE_KEY))
                .thenReturn(Optional.of(new AppSetting(VapidKeyStore.PRIVATE_KEY, "stored-private")));

        VapidKeys keys = store.loadOrCreate("mailto:t@test.lt");

        assertEquals("stored-public", keys.publicKey());
        assertEquals("stored-private", keys.privateKey());
        verify(settings, never()).save(any(AppSetting.class));
    }

    @Test
    void TA65_regeneratesBothKeysWhenOnlyOneIsStored() {
        when(settings.findById(VapidKeyStore.PUBLIC_KEY))
                .thenReturn(Optional.of(new AppSetting(VapidKeyStore.PUBLIC_KEY, "orphan-public")));
        when(settings.findById(VapidKeyStore.PRIVATE_KEY)).thenReturn(Optional.empty());

        VapidKeys keys = store.loadOrCreate("mailto:t@test.lt");

        assertTrue(!"orphan-public".equals(keys.publicKey()));
        verify(settings, times(2)).save(any(AppSetting.class));
    }
}