package lt.reviv.push.service;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.push.config.PushProperties;
import lt.reviv.push.config.ReminderProperties;
import lt.reviv.push.domain.PushDelivery;
import lt.reviv.push.repository.PushDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * UVP1 - reminder scheduler logic (US-12). Every minute it finds the reminders from the US-11 plan that are due
 * now and sends them in parallel (NFR-08: up to 1000 reminders within 1 minute).
 */
@Service
public class ReminderDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatchService.class);
    private static final int MAX_PARALLEL = 8;

    private final PushDeliveryRepository deliveries;
    private final PushNotificationService notifications;
    private final PushProperties pushProperties;
    private final ReminderProperties properties;
    private final Clock clock;

    public ReminderDispatchService(PushDeliveryRepository deliveries,
                                   PushNotificationService notifications,
                                   PushProperties pushProperties,
                                   ReminderProperties properties,
                                   Clock clock) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.pushProperties = pushProperties;
        this.properties = properties;
        this.clock = clock;
    }

    /** Sends all reminders that are due now; returns how many notifications were sent. */
    public int dispatchDue() {
        if (!pushProperties.configured()) {
            log.debug("Reminders skipped: VAPID keys are not configured");
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime until = now.toLocalTime();
        LocalTime since = until.minusMinutes(properties.catchUpMinutes());
        if (since.isAfter(until)) {
            since = LocalTime.MIN;
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (DueReminder reminder : deliveries.findDueIntake(
                since, until, today, ConsumptionPlan.Status.ACTIVE, PushDelivery.Kind.INTAKE)) {
            tasks.add(() -> deliver(PushDelivery.Kind.INTAKE, reminder.reminderId(), today,
                    reminder.userId(), ReminderMessages.intake(reminder)));
        }
        if (until.getHour() >= properties.cycleNoticeHour()) {
            for (CycleEntry entry : deliveries.findCycleEntries(
                    today.plusDays(3), today, ConsumptionPlan.Status.ACTIVE, PushDelivery.Kind.CYCLE_ENDING)) {
                tasks.add(() -> deliver(PushDelivery.Kind.CYCLE_ENDING, entry.entryId(), today,
                        entry.userId(), ReminderMessages.cycleEnding(entry)));
            }
            for (CycleEntry entry : deliveries.findCycleEntries(
                    today.minusDays(1), today, ConsumptionPlan.Status.ACTIVE, PushDelivery.Kind.CYCLE_ENDED)) {
                tasks.add(() -> deliver(PushDelivery.Kind.CYCLE_ENDED, entry.entryId(), today,
                        entry.userId(), ReminderMessages.cycleEnded(entry)));
            }
        }
        return runAll(tasks);
    }

    private int runAll(List<Callable<Boolean>> tasks) {
        if (tasks.isEmpty()) {
            return 0;
        }
        Semaphore limit = new Semaphore(MAX_PARALLEL);
        List<Callable<Boolean>> limited = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            limited.add(() -> {
                limit.acquire();
                try {
                    return task.call();
                } finally {
                    limit.release();
                }
            });
        }
        int sent = 0;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Boolean> future : executor.invokeAll(limited)) {
                try {
                    if (future.get()) {
                        sent++;
                    }
                } catch (ExecutionException e) {
                    log.warn("Reminder delivery failed: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return sent;
    }

    /** Claims the delivery first (unique row) so the same reminder is never sent twice. */
    private boolean deliver(PushDelivery.Kind kind, Long subjectId, LocalDate date, Long userId, PushMessage message) {
        PushDelivery delivery;
        try {
            delivery = deliveries.saveAndFlush(new PushDelivery(kind, subjectId, date));
        } catch (DataIntegrityViolationException alreadyClaimed) {
            return false;
        }
        PushNotificationService.Outcome outcome = notifications.sendToUser(userId, message);
        PushDelivery.Status status;
        if (outcome.sent() > 0) {
            status = PushDelivery.Status.SENT;
        } else if (outcome.subscriptions() == 0) {
            status = PushDelivery.Status.NO_SUBSCRIPTION;
        } else {
            status = PushDelivery.Status.FAILED;
        }
        delivery.complete(status);
        deliveries.save(delivery);
        return status == PushDelivery.Status.SENT;
    }
}