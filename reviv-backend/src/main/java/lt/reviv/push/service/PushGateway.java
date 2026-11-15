package lt.reviv.push.service;

import lt.reviv.push.domain.PushSubscription;

/** Sends one notification to one subscription. Replaced by a fake implementation in tests. */
public interface PushGateway {

    enum Result {
        /** The browser's push service accepted the notification. */
        SENT,
        /** The subscription is no longer valid (404/410) and must be deleted. */
        GONE,
        /** Temporary or other failure. */
        FAILED
    }

    Result send(PushSubscription subscription, PushMessage message);
}