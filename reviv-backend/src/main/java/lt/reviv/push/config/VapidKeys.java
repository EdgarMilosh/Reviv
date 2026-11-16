package lt.reviv.push.config;

/** The VAPID key pair (and contact) the server signs Web Push notifications with. */
public record VapidKeys(String publicKey, String privateKey, String subject) {

    public VapidKeys {
        publicKey = publicKey == null ? "" : publicKey.trim();
        privateKey = privateKey == null ? "" : privateKey.trim();
        subject = subject == null || subject.isBlank() ? "mailto:admin@reviv.local" : subject.trim();
    }

    /** True when both keys are present. */
    public boolean configured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }
}