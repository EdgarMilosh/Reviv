package lt.reviv.service;

import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.push.service.PushMessage;
import lt.reviv.push.service.PushNotificationService;
import lt.reviv.push.service.PushTestService;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-12: the test notification a user can send to their own devices. */
@ExtendWith(MockitoExtension.class)
class PushTestServiceTest {

    @Mock
    private AppUserRepository users;

    @Mock
    private PushNotificationService notifications;

    @InjectMocks
    private PushTestService service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token").header("alg", "none").subject("google-1").build();
    }

    @Test
    void TA62_sendsTestNotificationToTheSignedInUser() {
        AppUser user = new AppUser("google-1", "a@gmail.com", "A", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 5L);
        when(users.findByGoogleId("google-1")).thenReturn(Optional.of(user));
        PushNotificationService.Outcome expected = new PushNotificationService.Outcome(1, 1, 0);
        when(notifications.sendToUser(eq(5L), any(PushMessage.class))).thenReturn(expected);

        PushNotificationService.Outcome outcome = service.sendTest(jwt());

        assertEquals(expected, outcome);
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(notifications).sendToUser(eq(5L), message.capture());
        assertEquals("Bandomasis priminimas", message.getValue().title());
        assertEquals("/plans", message.getValue().url());
    }

    @Test
    void TA62_testNotificationRequiresRegisteredUser() {
        when(users.findByGoogleId("google-1")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.sendTest(jwt()));

        verifyNoInteractions(notifications);
    }
}