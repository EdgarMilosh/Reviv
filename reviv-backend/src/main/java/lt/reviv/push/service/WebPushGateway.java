package lt.reviv.push.service;

import lt.reviv.push.config.VapidKeys;
import lt.reviv.push.domain.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.Security;

/** Real Web Push delivery using VAPID (library nl.martijndwars:web-push). */
@Component
public class WebPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(WebPushGateway.class);

    static {
        // The library looks up the "BC" provider by name when it parses a subscription's keys,
        // so BouncyCastle must be registered before the first Notification is created.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final VapidKeys keys;
    private volatile PushService pushService;

    public WebPushGateway(VapidKeys keys) {
        this.keys = keys;
    }

    @Override
    public Result send(PushSubscription subscription, PushMessage message) {
        if (!keys.configured()) {
            log.warn("Web Push not sent: VAPID keys are not available");
            return Result.FAILED;
        }
        try {
            Notification notification = new Notification(
                    new Subscription(subscription.getEndpoint(),
                            new Subscription.Keys(subscription.getP256dh(), subscription.getAuth())),
                    message.toJson());
            var response = service().send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            if (status >= 200 && status < 300) {
                return Result.SENT;
            }
            log.warn("Web Push service returned {}", status);
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("Failed to send Web Push notification", e);
            return Result.FAILED;
        }
    }

    private PushService service() throws GeneralSecurityException {
        PushService current = pushService;
        if (current == null) {
            synchronized (this) {
                if (pushService == null) {
                    pushService = new PushService(keys.publicKey(), keys.privateKey(), keys.subject());
                }
                current = pushService;
            }
        }
        return current;
    }
}