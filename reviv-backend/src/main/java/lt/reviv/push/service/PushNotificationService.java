package lt.reviv.push.service;

import lt.reviv.push.domain.PushSubscription;
import lt.reviv.push.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Sends a notification to all of a user's devices and removes subscriptions that are no longer valid. */
@Service
public class PushNotificationService {

    public record Outcome(int subscriptions, int sent, int failed) {
    }

    private final PushSubscriptionRepository subscriptionRepository;
    private final PushGateway gateway;

    public PushNotificationService(PushSubscriptionRepository subscriptionRepository, PushGateway gateway) {
        this.subscriptionRepository = subscriptionRepository;
        this.gateway = gateway;
    }

    public Outcome sendToUser(Long userId, PushMessage message) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        int sent = 0;
        int failed = 0;
        for (PushSubscription subscription : subscriptions) {
            PushGateway.Result result = gateway.send(subscription, message);
            switch (result) {
                case SENT -> sent++;
                case GONE -> subscriptionRepository.delete(subscription);
                case FAILED -> failed++;
            }
        }
        return new Outcome(subscriptions.size(), sent, failed);
    }
}