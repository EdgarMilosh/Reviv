package lt.reviv.service;

import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.push.config.PushProperties;
import lt.reviv.push.domain.PushSubscription;
import lt.reviv.push.dto.PushSubscriptionRequest;
import lt.reviv.push.repository.PushSubscriptionRepository;
import lt.reviv.push.service.PushSubscriptionService;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-12: browser subscriptions (DP5 - user management). */
@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final String ENDPOINT = "https://push.example.com/abc";

    @Mock
    private PushSubscriptionRepository subscriptions;

    @Mock
    private AppUserRepository users;

    private PushSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PushSubscriptionService(subscriptions, users,
                new PushProperties("public-key", "private-key", "mailto:t@test.lt"));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token").header("alg", "none").subject("google-1").build();
    }

    private static AppUser user(long id) {
        AppUser user = new AppUser("google-1", "a@gmail.com", "A", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static PushSubscriptionRequest request() {
        return new PushSubscriptionRequest(ENDPOINT, new PushSubscriptionRequest.Keys("new-p256dh", "new-auth"));
    }

    @Test
    void TA62_subscribeStoresNewDevice() {
        when(users.findByGoogleId("google-1")).thenReturn(Optional.of(user(5L)));
        when(subscriptions.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());

        service.subscribe(jwt(), request());

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptions).save(captor.capture());
        PushSubscription saved = captor.getValue();
        assertEquals(5L, saved.getUserId().longValue());
        assertEquals(ENDPOINT, saved.getEndpoint());
        assertEquals("new-p256dh", saved.getP256dh());
        assertEquals("new-auth", saved.getAuth());
    }

    @Test
    void TA62_subscribeMovesKnownBrowserToCurrentUser() {
        PushSubscription existing = new PushSubscription(1L, ENDPOINT, "old-p256dh", "old-auth");
        when(users.findByGoogleId("google-1")).thenReturn(Optional.of(user(5L)));
        when(subscriptions.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));

        service.subscribe(jwt(), request());

        assertEquals(5L, existing.getUserId().longValue());
        assertEquals("new-p256dh", existing.getP256dh());
        assertEquals("new-auth", existing.getAuth());
        verify(subscriptions, never()).save(any(PushSubscription.class));
    }

    @Test
    void TA62_unsubscribeRemovesOnlyOwnDevice() {
        when(users.findByGoogleId("google-1")).thenReturn(Optional.of(user(5L)));

        service.unsubscribe(jwt(), ENDPOINT);

        verify(subscriptions).deleteByEndpointAndUserId(ENDPOINT, 5L);
    }

    @Test
    void TA62_subscribeRequiresRegisteredUser() {
        when(users.findByGoogleId("google-1")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.subscribe(jwt(), request()));

        verifyNoInteractions(subscriptions);
    }

    @Test
    void TA62_publicKeyComesFromConfiguration() {
        assertEquals("public-key", service.publicKey());
    }
}