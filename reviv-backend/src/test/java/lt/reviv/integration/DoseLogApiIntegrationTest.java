package lt.reviv.integration;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-13 integration test: schedule, marks, guidance, cancelling and history against a real PostgreSQL. */
class DoseLogApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vilnius");
    private static final AtomicLong GENERATION = new AtomicLong(7_000_000);
    private static final AtomicLong RECOMMENDATION = new AtomicLong(8_000_000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BloodTestRepository bloodTests;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final LocalDate today = LocalDate.now(ZONE);

    private record Fixture(AppUser user, Long planId, Long zincEntry, Long magnesiumEntry) {
    }

    private AppUser newUser() {
        String id = UUID.randomUUID().toString();
        return users.save(new AppUser("g-" + id, id + "@test.lt", "Test", UserRole.USER));
    }

    private static RequestPostProcessor asUser(AppUser user) {
        return jwt().jwt(j -> j.subject(user.getGoogleId()).claim("email", user.getEmail()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** Plan rows reference recommendation tables that are irrelevant here, so FK checks are relaxed. */
    private <T> T relaxed(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            return work.get();
        });
    }

    private Long insertEntry(Long planId, String name, String dayPart, LocalDate start, LocalDate end) {
        return relaxed(() -> jdbc.queryForObject(
                "insert into consumption_plan_entry (plan_id, recommendation_id, supplement_id, "
                        + "supplement_name_snapshot, daily_dose, dose_unit, day_part, meal_relation, start_date, end_date) "
                        + "values (?, ?, (select id from supplement where name = 'Kalcis'), ?, 500.0, 'mg', ?, "
                        + "'AFTER_MEAL', ?, ?) returning id",
                Long.class, planId, RECOMMENDATION.incrementAndGet(), name, dayPart, start, end));
    }

    private Fixture plan(String status, LocalDate start, LocalDate end) {
        AppUser user = newUser();
        Long testId = bloodTests.save(new BloodTest(user.getId(), "test.pdf", 3L, "a".repeat(64), new byte[]{1, 2, 3})).getId();
        Long planId = relaxed(() -> jdbc.queryForObject(
                "insert into consumption_plan (blood_test_id, recommendation_generation_id, ai_model, status, "
                        + "created_at, updated_at) values (?, ?, 'test-model', ?, now(), now()) returning id",
                Long.class, testId, GENERATION.incrementAndGet(), status));
        return new Fixture(user, planId, insertEntry(planId, "Cinkas", "MORNING", start, end),
                insertEntry(planId, "Magnis", "NOON", start, end));
    }

    private String doseUrl(Fixture fixture, Long entryId, LocalDate day) {
        return "/api/planai/" + fixture.planId() + "/entries/" + entryId + "/doses/" + day;
    }

    private static String body(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    @Test
    void TA50_dailyScheduleShowsTheDosesAndMarksAreSaved() throws Exception {
        Fixture fixture = plan("ACTIVE", today.minusDays(2), today.plusDays(10));
        String scheduleUrl = "/api/planai/" + fixture.planId() + "/schedule";

        mvc.perform(get(scheduleUrl).with(asUser(fixture.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.doses.length()").value(2))
                .andExpect(jsonPath("$.doses[0].supplementName").value("Cinkas"))
                .andExpect(jsonPath("$.doses[0].time").value(startsWith("08:00")))
                .andExpect(jsonPath("$.doses[0].state").value("PENDING"))
                .andExpect(jsonPath("$.doses[1].supplementName").value("Magnis"));

        mvc.perform(put(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TAKEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dose.state").value("TAKEN"))
                .andExpect(jsonPath("$.dose.canCancel").value(true))
                .andExpect(jsonPath("$.guidance").doesNotExist());

        mvc.perform(get(scheduleUrl).with(asUser(fixture.user())))
                .andExpect(jsonPath("$.doses[0].state").value("TAKEN"))
                .andExpect(jsonPath("$.doses[1].state").value("PENDING"));
    }

    @Test
    void TA66_missedDoseReturnsGuidanceAndTheCatalogRule() throws Exception {
        Fixture fixture = plan("ACTIVE", today.minusDays(2), today.plusDays(10));

        mvc.perform(put(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("SKIPPED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dose.state").value("SKIPPED"))
                .andExpect(jsonPath("$.guidance.action").isNotEmpty())
                .andExpect(jsonPath("$.guidance.message").isNotEmpty())
                .andExpect(jsonPath("$.guidance.supplementRule").isNotEmpty());
    }

    @Test
    void TA69_markCanBeCancelledOnTheSameDayOnly() throws Exception {
        Fixture fixture = plan("ACTIVE", today.minusDays(2), today.plusDays(10));
        mvc.perform(put(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TAKEN")))
                .andExpect(status().isOk());

        mvc.perform(delete(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(fixture.user())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/planai/" + fixture.planId() + "/schedule").with(asUser(fixture.user())))
                .andExpect(jsonPath("$.doses[0].state").value("PENDING"));
        mvc.perform(delete(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(fixture.user())))
                .andExpect(status().isNotFound());
        mvc.perform(delete(doseUrl(fixture, fixture.zincEntry(), today.minusDays(1))).with(asUser(fixture.user())))
                .andExpect(status().isConflict());
    }

    @Test
    void TA70_onlyTodayInAnActivePlanOfTheOwnerCanBeMarked() throws Exception {
        Fixture fixture = plan("ACTIVE", today.minusDays(2), today.plusDays(10));
        Fixture draft = plan("DRAFT", today.minusDays(2), today.plusDays(10));

        mvc.perform(put(doseUrl(fixture, fixture.zincEntry(), today.plusDays(1))).with(asUser(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TAKEN")))
                .andExpect(status().isBadRequest());
        mvc.perform(put(doseUrl(draft, draft.zincEntry(), today)).with(asUser(draft.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TAKEN")))
                .andExpect(status().isConflict());
        mvc.perform(put(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(newUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TAKEN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/planai/" + fixture.planId() + "/schedule").with(asUser(newUser())))
                .andExpect(status().isNotFound());
        mvc.perform(put(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void TA67_historyCountsTheMarkedDosesAndTheAdherence() throws Exception {
        Fixture fixture = plan("ACTIVE", today.minusDays(2), today.plusDays(10));
        mvc.perform(put(doseUrl(fixture, fixture.zincEntry(), today)).with(asUser(fixture.user()))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TAKEN")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/planai/" + fixture.planId() + "/history").with(asUser(fixture.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planned").value(5))
                .andExpect(jsonPath("$.taken").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.unmarked").value(4))
                .andExpect(jsonPath("$.adherencePercent").value(20))
                .andExpect(jsonPath("$.cycleEnded").value(false))
                .andExpect(jsonPath("$.entries.length()").value(2));

        mvc.perform(get("/api/planai/" + fixture.planId() + "/history")
                        .param("from", today.toString()).param("to", today.minusDays(1).toString())
                        .with(asUser(fixture.user())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void TA51_finishedCycleWithoutTakenDosesShowsThePlanWasNotFollowed() throws Exception {
        Fixture fixture = plan("ACTIVE", today.minusDays(10), today.minusDays(1));

        mvc.perform(get("/api/planai/" + fixture.planId() + "/history").with(asUser(fixture.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleEnded").value(true))
                .andExpect(jsonPath("$.notFollowed").value(true))
                .andExpect(jsonPath("$.notice").isNotEmpty())
                .andExpect(jsonPath("$.taken").value(0))
                .andExpect(jsonPath("$.adherencePercent").value(0));
    }
}