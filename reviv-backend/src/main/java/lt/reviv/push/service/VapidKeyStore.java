package lt.reviv.push.service;

import lt.reviv.common.settings.AppSetting;
import lt.reviv.common.settings.AppSettingRepository;
import lt.reviv.push.config.VapidKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Keeps the generated VAPID key pair in the database so it survives restarts. */
@Service
public class VapidKeyStore {

    public static final String PUBLIC_KEY = "vapid.public-key";
    public static final String PRIVATE_KEY = "vapid.private-key";

    private final AppSettingRepository settings;

    public VapidKeyStore(AppSettingRepository settings) {
        this.settings = settings;
    }

    /** Returns the stored key pair; on first use a new one is generated and saved. */
    @Transactional
    public VapidKeys loadOrCreate(String subject) {
        Optional<AppSetting> publicKey = settings.findById(PUBLIC_KEY);
        Optional<AppSetting> privateKey = settings.findById(PRIVATE_KEY);
        if (publicKey.isPresent() && privateKey.isPresent()) {
            return new VapidKeys(publicKey.get().getValue(), privateKey.get().getValue(), subject);
        }
        VapidKeys generated = VapidKeyGenerator.generate(subject);
        settings.save(new AppSetting(PUBLIC_KEY, generated.publicKey()));
        settings.save(new AppSetting(PRIVATE_KEY, generated.privateKey()));
        return generated;
    }
}