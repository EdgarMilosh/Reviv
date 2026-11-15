package lt.reviv.push.service;

import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.repository.AppUserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Test notification: lets a user check that their browser receives reminders. */
@Service
public class PushTestService {

    private final AppUserRepository userRepository;
    private final PushNotificationService notifications;

    public PushTestService(AppUserRepository userRepository, PushNotificationService notifications) {
        this.userRepository = userRepository;
        this.notifications = notifications;
    }

    public PushNotificationService.Outcome sendTest(Jwt jwt) {
        Long userId = userRepository.findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new)
                .getId();
        return notifications.sendToUser(userId, new PushMessage(
                "Bandomasis priminimas", "Priminimai veikia. Taip atrodys jūsų pranešimai.", "/plans"));
    }
}