package lt.reviv.controller;

import lt.reviv.push.controller.PushController;
import lt.reviv.push.dto.PublicKeyResponse;
import lt.reviv.push.dto.PushSubscriptionRequest;
import lt.reviv.push.service.PushNotificationService;
import lt.reviv.push.service.PushSubscriptionService;
import lt.reviv.push.service.PushTestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-12: Web Push API controller. */
@ExtendWith(MockitoExtension.class)
class PushControllerTest {

    @Mock
    private PushSubscriptionService service;

    @Mock
    private PushTestService testService;

    @InjectMocks
    private PushController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token").header("alg", "none").subject("google-1").build();
    }

    @Test
    void TA62_publicKeyIsReturnedFromService() {
        when(service.publicKey()).thenReturn("public-key");

        PublicKeyResponse response = controller.publicKey();

        assertEquals("public-key", response.publicKey());
    }

    @Test
    void TA62_subscribeDelegatesToService() {
        Jwt jwt = jwt();
        PushSubscriptionRequest request =
                new PushSubscriptionRequest("https://push.example.com/abc", new PushSubscriptionRequest.Keys("a", "b"));

        controller.subscribe(jwt, request);

        verify(service).subscribe(jwt, request);
    }

    @Test
    void TA62_unsubscribeDelegatesToService() {
        Jwt jwt = jwt();

        controller.unsubscribe(jwt, "https://push.example.com/abc");

        verify(service).unsubscribe(jwt, "https://push.example.com/abc");
    }

    @Test
    void TA62_testNotificationDelegatesToService() {
        Jwt jwt = jwt();
        PushNotificationService.Outcome outcome = new PushNotificationService.Outcome(2, 2, 0);
        when(testService.sendTest(jwt)).thenReturn(outcome);

        assertSame(outcome, controller.sendTest(jwt));
    }
}