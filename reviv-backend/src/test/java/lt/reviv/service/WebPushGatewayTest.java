package lt.reviv.service;

import lt.reviv.push.config.PushProperties;
import lt.reviv.push.domain.PushSubscription;
import lt.reviv.push.service.PushGateway;
import lt.reviv.push.service.PushMessage;
import lt.reviv.push.service.WebPushGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** US-12: without VAPID keys nothing is sent and nothing crashes. */
class WebPushGatewayTest {

    @Test
    void TA59_gatewayDoesNotSendWithoutVapidKeys() {
        WebPushGateway gateway = new WebPushGateway(new PushProperties("", "", ""));

        PushGateway.Result result = gateway.send(
                new PushSubscription(1L, "https://push.example.com/abc", "p256dh", "auth"),
                new PushMessage("Title", "Body", "/"));

        assertEquals(PushGateway.Result.FAILED, result);
    }
}