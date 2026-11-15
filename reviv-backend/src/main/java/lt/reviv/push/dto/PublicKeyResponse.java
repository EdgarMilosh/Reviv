package lt.reviv.push.dto;

/** Public VAPID key used by the browser to create a subscription; empty if not configured on the server. */
public record PublicKeyResponse(String publicKey) {
}