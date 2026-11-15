package lt.reviv.push.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** UVP1 - reminder scheduler: calls the delivery logic every minute. */
@Component
@ConditionalOnProperty(prefix = "reviv.reminders", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderDispatchService dispatchService;

    public ReminderScheduler(ReminderDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${reviv.reminders.poll-ms:60000}",
            initialDelayString = "${reviv.reminders.poll-ms:60000}")
    public void tick() {
        try {
            int sent = dispatchService.dispatchDue();
            if (sent > 0) {
                log.info("Reminders sent: {}", sent);
            }
        } catch (RuntimeException e) {
            log.error("Reminder dispatch failed", e);
        }
    }
}