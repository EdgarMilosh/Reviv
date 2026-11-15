package lt.reviv.service;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.push.config.PushProperties;
import lt.reviv.push.config.ReminderProperties;
import lt.reviv.push.domain.PushDelivery;
import lt.reviv.push.repository.PushDeliveryRepository;
import lt.reviv.push.service.CycleEntry;
import lt.reviv.push.service.DueReminder;
import lt.reviv.push.service.PushMessage;
import lt.reviv.push.service.PushNotificationService;
import lt.reviv.push.service.ReminderDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-12: reminder scheduler logic (UVP1) with a fixed clock and a fake push sender. */
@ExtendWith(MockitoExtension.class)
class ReminderDispatchServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vilnius");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final PushProperties CONFIGURED = new PushProperties("public", "private", "mailto:t@test.lt");
    private static final ReminderProperties SETTINGS = new ReminderProperties(true, "Europe/Vilnius", 10, 9);

    @Mock
    private PushDeliveryRepository deliveries;

    @Mock
    private PushNotificationService notifications;

    private ReminderDispatchService service(PushProperties push, LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new ReminderDispatchService(deliveries, notifications, push, SETTINGS, clock);
    }

    private ReminderDispatchService service(LocalDateTime now) {
        return service(CONFIGURED, now);
    }

    private static DueReminder reminder(long id, long userId) {
        return new DueReminder(id, id + 100, 7L, userId, "Cinkas", new BigDecimal("15.0000"), "mg",
                PlanEntry.MealRelation.AFTER_MEAL);
    }

    private static CycleEntry cycleEntry(long id, long userId, LocalDate endDate) {
        return new CycleEntry(id, 7L, userId, "Selenas", endDate);
    }

    private void stubDueIntake(LocalTime since, LocalTime until, List<DueReminder> result) {
        when(deliveries.findDueIntake(since, until, TODAY, ConsumptionPlan.Status.ACTIVE, PushDelivery.Kind.INTAKE))
                .thenReturn(result);
    }

    private PushDelivery savedDelivery() {
        ArgumentCaptor<PushDelivery> captor = ArgumentCaptor.forClass(PushDelivery.class);
        verify(deliveries).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void TA49_sendsDueReminderAndMarksItSent() {
        stubDueIntake(LocalTime.of(7, 50, 30), LocalTime.of(8, 0, 30), List.of(reminder(5L, 8L)));
        when(deliveries.saveAndFlush(any(PushDelivery.class))).thenAnswer(i -> i.getArgument(0));
        when(notifications.sendToUser(eq(8L), any(PushMessage.class)))
                .thenReturn(new PushNotificationService.Outcome(1, 1, 0));

        int sent = service(LocalDateTime.of(2026, 11, 16, 8, 0, 30)).dispatchDue();

        assertEquals(1, sent);
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(notifications).sendToUser(eq(8L), message.capture());
        assertEquals("Laikas išgerti Cinkas", message.getValue().title());
        assertEquals("15 mg, po valgio", message.getValue().body());
        PushDelivery delivery = savedDelivery();
        assertEquals(PushDelivery.Status.SENT, delivery.getStatus());
        assertNotNull(delivery.getSentAt());
        assertEquals(PushDelivery.Kind.INTAKE, delivery.getKind());
        assertEquals(5L, delivery.getSubjectId().longValue());
        assertEquals(TODAY, delivery.getDeliveryDate());
    }

    @Test
    void TA60_skipsReminderThatWasAlreadyClaimed() {
        stubDueIntake(LocalTime.of(7, 50), LocalTime.of(8, 0), List.of(reminder(5L, 8L)));
        when(deliveries.saveAndFlush(any(PushDelivery.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        int sent = service(LocalDateTime.of(2026, 11, 16, 8, 0)).dispatchDue();

        assertEquals(0, sent);
        verifyNoInteractions(notifications);
        verify(deliveries, never()).save(any(PushDelivery.class));
    }

    @Test
    void TA60_marksNoSubscriptionWhenUserHasNoDevice() {
        stubDueIntake(LocalTime.of(7, 50), LocalTime.of(8, 0), List.of(reminder(5L, 8L)));
        when(deliveries.saveAndFlush(any(PushDelivery.class))).thenAnswer(i -> i.getArgument(0));
        when(notifications.sendToUser(eq(8L), any(PushMessage.class)))
                .thenReturn(new PushNotificationService.Outcome(0, 0, 0));

        int sent = service(LocalDateTime.of(2026, 11, 16, 8, 0)).dispatchDue();

        assertEquals(0, sent);
        assertEquals(PushDelivery.Status.NO_SUBSCRIPTION, savedDelivery().getStatus());
    }

    @Test
    void TA60_marksFailedWhenEveryDeviceRejects() {
        stubDueIntake(LocalTime.of(7, 50), LocalTime.of(8, 0), List.of(reminder(5L, 8L)));
        when(deliveries.saveAndFlush(any(PushDelivery.class))).thenAnswer(i -> i.getArgument(0));
        when(notifications.sendToUser(eq(8L), any(PushMessage.class)))
                .thenReturn(new PushNotificationService.Outcome(2, 0, 2));

        int sent = service(LocalDateTime.of(2026, 11, 16, 8, 0)).dispatchDue();

        assertEquals(0, sent);
        assertEquals(PushDelivery.Status.FAILED, savedDelivery().getStatus());
    }

    @Test
    void TA58_sendsCycleNoticesAfterNoticeHour() {
        stubDueIntake(LocalTime.of(9, 20), LocalTime.of(9, 30), List.of());
        when(deliveries.findCycleEntries(TODAY.plusDays(3), TODAY, ConsumptionPlan.Status.ACTIVE,
                PushDelivery.Kind.CYCLE_ENDING)).thenReturn(List.of(cycleEntry(31L, 9L, TODAY.plusDays(3))));
        when(deliveries.findCycleEntries(TODAY.minusDays(1), TODAY, ConsumptionPlan.Status.ACTIVE,
                PushDelivery.Kind.CYCLE_ENDED)).thenReturn(List.of(cycleEntry(32L, 9L, TODAY.minusDays(1))));
        when(deliveries.saveAndFlush(any(PushDelivery.class))).thenAnswer(i -> i.getArgument(0));
        when(notifications.sendToUser(eq(9L), any(PushMessage.class)))
                .thenReturn(new PushNotificationService.Outcome(1, 1, 0));

        int sent = service(LocalDateTime.of(2026, 11, 16, 9, 30)).dispatchDue();

        assertEquals(2, sent);
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(notifications, times(2)).sendToUser(eq(9L), messages.capture());
        Set<String> titles = messages.getAllValues().stream().map(PushMessage::title).collect(Collectors.toSet());
        assertEquals(Set.of("Netrukus baigsis ciklas", "Ciklas baigėsi"), titles);
    }

    @Test
    void TA58_doesNotLookForCycleNoticesBeforeNoticeHour() {
        stubDueIntake(LocalTime.of(8, 49), LocalTime.of(8, 59), List.of());

        int sent = service(LocalDateTime.of(2026, 11, 16, 8, 59)).dispatchDue();

        assertEquals(0, sent);
        verify(deliveries, never()).findCycleEntries(any(LocalDate.class), any(LocalDate.class),
                any(ConsumptionPlan.Status.class), any(PushDelivery.Kind.class));
    }

    @Test
    void TA59_sendsNothingWithoutVapidKeys() {
        int sent = service(new PushProperties("", "", ""), LocalDateTime.of(2026, 11, 16, 8, 0)).dispatchDue();

        assertEquals(0, sent);
        verifyNoInteractions(deliveries, notifications);
    }

    @Test
    void TA49_catchUpWindowStartsAtMidnightShortlyAfterMidnight() {
        stubDueIntake(LocalTime.MIN, LocalTime.of(0, 3), List.of());

        int sent = service(LocalDateTime.of(2026, 11, 16, 0, 3)).dispatchDue();

        assertEquals(0, sent);
    }

    /** NFR-08: up to 1000 reminders must be processed within 1 minute. */
    @Test
    void TA61_sendsThousandRemindersWithinOneMinute() {
        List<DueReminder> due = IntStream.rangeClosed(1, 1000).mapToObj(i -> reminder(i, i)).toList();
        stubDueIntake(LocalTime.of(7, 50), LocalTime.of(8, 0), due);
        when(deliveries.saveAndFlush(any(PushDelivery.class))).thenAnswer(i -> i.getArgument(0));
        AtomicInteger calls = new AtomicInteger();
        when(notifications.sendToUser(anyLong(), any(PushMessage.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            Thread.sleep(5);
            return new PushNotificationService.Outcome(1, 1, 0);
        });

        long start = System.nanoTime();
        int sent = service(LocalDateTime.of(2026, 11, 16, 8, 0)).dispatchDue();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertEquals(1000, sent);
        assertEquals(1000, calls.get());
        assertTrue(elapsed.compareTo(Duration.ofMinutes(1)) < 0, "Took " + elapsed);
    }
}