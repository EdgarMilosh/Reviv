package lt.reviv.service;

import lt.reviv.push.config.PushProperties;
import lt.reviv.push.domain.PushSubscription;
import lt.reviv.push.service.PushGateway;
import lt.reviv.push.service.PushMessage;
import lt.reviv.push.service.WebPushGateway;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** US-12: without VAPID keys nothing is sent, and the crypto provider is ready before the first send. */
class WebPushGatewayTest {

    @Test
    void TA59_gatewayDoesNotSendWithoutVapidKeys() {
        WebPushGateway gateway = new WebPushGateway(new PushProperties("", "", ""));

        PushGateway.Result result = gateway.send(
                new PushSubscription(1L, "https://push.example.com/abc", "p256dh", "auth"),
                new PushMessage("Title", "Body", "/"));

        assertEquals(PushGateway.Result.FAILED, result);
    }

    @Test
    void TA59_bouncyCastleProviderIsRegisteredBeforeTheFirstSend() {
        new WebPushGateway(new PushProperties("", "", ""));

        assertNotNull(Security.getProvider("BC"),
                "The web-push library looks the provider up by the name BC when it parses subscription keys");
    }
}