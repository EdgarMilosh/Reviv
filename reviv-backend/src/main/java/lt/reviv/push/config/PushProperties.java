package lt.reviv.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** VAPID settings for Web Push notifications (reviv.push.*). */
@ConfigurationProperties(prefix = "reviv.push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public PushProperties {
        publicKey = publicKey == null ? "" : publicKey.trim();
        privateKey = privateKey == null ? "" : privateKey.trim();
        subject = subject == null || subject.isBlank() ? "mailto:admin@reviv.local" : subject.trim();
    }

    /** True when the server has VAPID keys configured. */
    public boolean configured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }
}