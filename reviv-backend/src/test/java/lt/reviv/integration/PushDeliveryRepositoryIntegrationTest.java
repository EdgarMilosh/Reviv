package lt.reviv.integration;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.push.domain.PushDelivery;
import lt.reviv.push.repository.PushDeliveryRepository;
import lt.reviv.push.service.CycleEntry;
import lt.reviv.push.service.DueReminder;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * US-12: the reminder queries against a real PostgreSQL with the real schema.
 * The database is shared with other integration tests, so every query result is filtered by this test's user.
 */
class PushDeliveryRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong GENERATION = new AtomicLong(5_000_000);
    private static final AtomicLong RECOMMENDATION = new AtomicLong(9_000_000);

    @Autowired
    private PushDeliveryRepository deliveries;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BloodTestRepository bloodTests;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final LocalDate today = LocalDate.now();

    private AppUser newUser() {
        String id = UUID.randomUUID().toString();
        return users.save(new AppUser("g-" + id, id + "@test.lt", "Test", UserRole.USER));
    }

    private Long newBloodTest(AppUser user) {
        return bloodTests.save(new BloodTest(user.getId(), "test.pdf", 3L, "a".repeat(64), new byte[]{1, 2, 3})).getId();
    }

    /** Plan, entries and reminders reference recommendation tables that are irrelevant here, so FK checks are relaxed. */
    private <T> T relaxed(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            return work.get();
        });
    }

    private Long insertPlan(Long bloodTestId, String status) {
        return relaxed(() -> jdbc.queryForObject(
                "insert into consumption_plan (blood_test_id, recommendation_generation_id, ai_model, status, "
                        + "created_at, updated_at) values (?, ?, 'test-model', ?, now(), now()) returning id",
                Long.class, bloodTestId, GENERATION.incrementAndGet(), status));
    }

    private boolean hasScheduledTime() {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema = current_schema() "
                        + "and table_name = 'consumption_plan_entry' and column_name = 'scheduled_time'",
                Integer.class);
        return count != null && count > 0;
    }

    private Long insertEntry(Long planId, String name, String doseUnit, String meal, LocalDate start, LocalDate end) {
        boolean withTime = hasScheduledTime();
        return relaxed(() -> jdbc.queryForObject(
                "insert into consumption_plan_entry (plan_id, recommendation_id, supplement_id, "
                        + "supplement_name_snapshot, daily_dose, dose_unit, day_part, meal_relation, start_date, end_date"
                        + (withTime ? ", scheduled_time" : "") + ") "
                        + "values (?, ?, (select id from supplement where name = 'Kalcis'), ?, 15.0, ?, 'MORNING', ?, ?, ?"
                        + (withTime ? ", TIME '08:00'" : "") + ") returning id",
                Long.class, planId, RECOMMENDATION.incrementAndGet(), name, doseUnit, meal, start, end));
    }

    private Long insertReminder(Long entryId, LocalTime time, boolean enabled) {
        return relaxed(() -> jdbc.queryForObject(
                "insert into consumption_plan_reminder (plan_entry_id, reminder_time, enabled) values (?, ?, ?) "
                        + "returning id",
                Long.class, entryId, java.sql.Time.valueOf(time), enabled));
    }

    private List<DueReminder> dueFor(AppUser user, LocalTime since, LocalTime until) {
        return deliveries.findDueIntake(since, until, today, ConsumptionPlan.Status.ACTIVE, PushDelivery.Kind.INTAKE)
                .stream().filter(d -> d.userId().equals(user.getId())).toList();
    }

    private List<CycleEntry> cycleFor(AppUser user, LocalDate endDate, PushDelivery.Kind kind) {
        return deliveries.findCycleEntries(endDate, today, ConsumptionPlan.Status.ACTIVE, kind)
                .stream().filter(c -> c.userId().equals(user.getId())).toList();
    }

    @Test
    void TA49_dueQueryReturnsOnlyActiveUndeliveredRemindersInsideTheWindow() {
        AppUser user = newUser();
        Long testId = newBloodTest(user);
        Long activePlan = insertPlan(testId, "ACTIVE");
        Long draftPlan = insertPlan(testId, "DRAFT");

        Long due = insertEntry(activePlan, "Cinkas", "mg", "AFTER_MEAL", today.minusDays(1), today.plusDays(10));
        Long dueReminder = insertReminder(due, LocalTime.of(8, 0), true);
        insertReminder(insertEntry(activePlan, "Magnis", "mg", "WITH_MEAL", today.minusDays(1), today.plusDays(10)),
                LocalTime.of(13, 0), true);
        insertReminder(insertEntry(activePlan, "Varis", "mg", "UNKNOWN", today.minusDays(20), today.minusDays(1)),
                LocalTime.of(8, 0), true);
        insertReminder(insertEntry(activePlan, "Selenas", "mcg", "UNKNOWN", today.minusDays(1), today.plusDays(10)),
                LocalTime.of(8, 0), false);
        insertReminder(insertEntry(draftPlan, "Geležis", "mg", "BEFORE_MEAL", today.minusDays(1), today.plusDays(10)),
                LocalTime.of(8, 0), true);

        List<DueReminder> found = dueFor(user, LocalTime.of(7, 55), LocalTime.of(8, 5));

        assertEquals(1, found.size());
        DueReminder reminder = found.get(0);
        assertEquals(dueReminder, reminder.reminderId());
        assertEquals(activePlan, reminder.planId());
        assertEquals("Cinkas", reminder.supplementName());
        assertEquals(0, reminder.dailyDose().compareTo(new java.math.BigDecimal("15")));
        assertEquals("mg", reminder.doseUnit());
        assertEquals(PlanEntry.MealRelation.AFTER_MEAL, reminder.mealRelation());

        deliveries.save(new PushDelivery(PushDelivery.Kind.INTAKE, dueReminder, today));

        assertTrue(dueFor(user, LocalTime.of(7, 55), LocalTime.of(8, 5)).isEmpty());
    }

    @Test
    void TA58_cycleQueryFindsOnlyActivePlanEntriesEndingOnTheGivenDay() {
        AppUser user = newUser();
        Long testId = newBloodTest(user);
        Long activePlan = insertPlan(testId, "ACTIVE");
        Long draftPlan = insertPlan(testId, "DRAFT");
        Long ending = insertEntry(activePlan, "Selenas", "mcg", "UNKNOWN", today.minusDays(5), today.plusDays(3));
        insertEntry(activePlan, "Jodas", "mcg", "UNKNOWN", today.minusDays(5), today.plusDays(4));
        insertEntry(draftPlan, "Varis", "mg", "UNKNOWN", today.minusDays(5), today.plusDays(3));

        List<CycleEntry> found = cycleFor(user, today.plusDays(3), PushDelivery.Kind.CYCLE_ENDING);

        assertEquals(1, found.size());
        assertEquals(ending, found.get(0).entryId());
        assertEquals(activePlan, found.get(0).planId());
        assertEquals("Selenas", found.get(0).supplementName());
        assertEquals(today.plusDays(3), found.get(0).endDate());

        deliveries.save(new PushDelivery(PushDelivery.Kind.CYCLE_ENDING, ending, today));

        assertTrue(cycleFor(user, today.plusDays(3), PushDelivery.Kind.CYCLE_ENDING).isEmpty());
        assertEquals(1, cycleFor(user, today.plusDays(3), PushDelivery.Kind.CYCLE_ENDED).size());
    }

    @Test
    void TA60_deliveryRowIsUniquePerReminderKindAndDay() {
        long subject = Math.abs(UUID.randomUUID().getMostSignificantBits());
        deliveries.saveAndFlush(new PushDelivery(PushDelivery.Kind.INTAKE, subject, today));

        assertThrows(DataIntegrityViolationException.class,
                () -> deliveries.saveAndFlush(new PushDelivery(PushDelivery.Kind.INTAKE, subject, today)));

        deliveries.saveAndFlush(new PushDelivery(PushDelivery.Kind.INTAKE, subject, today.plusDays(1)));
        deliveries.saveAndFlush(new PushDelivery(PushDelivery.Kind.CYCLE_ENDING, subject, today));
    }
}