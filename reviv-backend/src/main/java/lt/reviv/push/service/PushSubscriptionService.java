package lt.reviv.push.service;

import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.push.config.PushProperties;
import lt.reviv.push.domain.PushSubscription;
import lt.reviv.push.dto.PushSubscriptionRequest;
import lt.reviv.push.repository.PushSubscriptionRepository;
import lt.reviv.users.repository.AppUserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Management of browser Web Push subscriptions (US-12). */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final AppUserRepository userRepository;
    private final PushProperties properties;

    public PushSubscriptionService(PushSubscriptionRepository subscriptionRepository,
                                   AppUserRepository userRepository,
                                   PushProperties properties) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public String publicKey() {
        return properties.publicKey();
    }

    /** Saves the subscription; if the same browser endpoint already exists, updates owner and keys. */
    @Transactional
    public void subscribe(Jwt jwt, PushSubscriptionRequest request) {
        Long userId = currentUserId(jwt);
        subscriptionRepository.findByEndpoint(request.endpoint()).ifPresentOrElse(
                existing -> existing.reassign(userId, request.keys().p256dh(), request.keys().auth()),
                () -> subscriptionRepository.save(new PushSubscription(
                        userId, request.endpoint(), request.keys().p256dh(), request.keys().auth())));
    }

    @Transactional
    public void unsubscribe(Jwt jwt, String endpoint) {
        subscriptionRepository.deleteByEndpointAndUserId(endpoint, currentUserId(jwt));
    }

    private Long currentUserId(Jwt jwt) {
        return userRepository.findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new)
                .getId();
    }
}