package lt.reviv.service;

import lt.reviv.push.domain.PushSubscription;
import lt.reviv.push.repository.PushSubscriptionRepository;
import lt.reviv.push.service.PushGateway;
import lt.reviv.push.service.PushMessage;
import lt.reviv.push.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-12: delivery to all of a user's devices and clean-up of subscriptions that are gone. */
@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    private static final PushMessage MESSAGE = new PushMessage("Title", "Body", "/plans/1");

    @Mock
    private PushSubscriptionRepository subscriptions;

    @Mock
    private PushGateway gateway;

    @InjectMocks
    private PushNotificationService service;

    private static PushSubscription device(String endpoint) {
        return new PushSubscription(5L, endpoint, "p256dh", "auth");
    }

    @Test
    void TA62_sendsToEveryDeviceOfTheUser() {
        PushSubscription phone = device("https://push.example.com/phone");
        PushSubscription laptop = device("https://push.example.com/laptop");
        when(subscriptions.findByUserId(5L)).thenReturn(List.of(phone, laptop));
        when(gateway.send(eq(phone), any(PushMessage.class))).thenReturn(PushGateway.Result.SENT);
        when(gateway.send(eq(laptop), any(PushMessage.class))).thenReturn(PushGateway.Result.FAILED);

        PushNotificationService.Outcome outcome = service.sendToUser(5L, MESSAGE);

        assertEquals(2, outcome.subscriptions());
        assertEquals(1, outcome.sent());
        assertEquals(1, outcome.failed());
        verify(subscriptions, never()).delete(any(PushSubscription.class));
    }

    @Test
    void TA62_removesSubscriptionThatIsNoLongerValid() {
        PushSubscription gone = device("https://push.example.com/gone");
        when(subscriptions.findByUserId(5L)).thenReturn(List.of(gone));
        when(gateway.send(eq(gone), any(PushMessage.class))).thenReturn(PushGateway.Result.GONE);

        PushNotificationService.Outcome outcome = service.sendToUser(5L, MESSAGE);

        verify(subscriptions).delete(gone);
        assertEquals(1, outcome.subscriptions());
        assertEquals(0, outcome.sent());
        assertEquals(0, outcome.failed());
    }

    @Test
    void TA62_reportsNoSubscriptionsForUserWithoutDevices() {
        when(subscriptions.findByUserId(5L)).thenReturn(List.of());

        PushNotificationService.Outcome outcome = service.sendToUser(5L, MESSAGE);

        assertEquals(new PushNotificationService.Outcome(0, 0, 0), outcome);
        verifyNoInteractions(gateway);
    }
}