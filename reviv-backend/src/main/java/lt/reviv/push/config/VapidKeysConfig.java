package lt.reviv.push.config;

import lt.reviv.push.service.VapidKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the VAPID keys: keys from the environment (VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY) win;
 * otherwise a key pair is generated once and stored in the database.
 */
@Configuration
public class VapidKeysConfig {

    private static final Logger log = LoggerFactory.getLogger(VapidKeysConfig.class);

    @Bean
    public VapidKeys vapidKeys(PushProperties properties, VapidKeyStore store) {
        if (properties.configured()) {
            log.info("Using VAPID keys from the environment");
            return new VapidKeys(properties.publicKey(), properties.privateKey(), properties.subject());
        }
        log.info("No VAPID keys in the environment: using the keys stored in the database (generated on first start)");
        return store.loadOrCreate(properties.subject());
    }
}