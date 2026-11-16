package lt.reviv.service;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.domain.plan.PlanReminder;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.bloodtests.repository.plan.PlanReminderRepository;
import lt.reviv.doses.config.AdherenceProperties;
import lt.reviv.doses.domain.DoseLog;
import lt.reviv.doses.dto.DaySchedule;
import lt.reviv.doses.dto.DoseState;
import lt.reviv.doses.dto.HistorySummary;
import lt.reviv.doses.dto.MarkResponse;
import lt.reviv.doses.repository.DoseLogRepository;
import lt.reviv.doses.service.DoseLogService;
import lt.reviv.supplements.domain.Supplement;
import lt.reviv.supplements.domain.SupplementForm;
import lt.reviv.supplements.repository.SupplementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-13: daily schedule, dose marks, guidance, cancelling and history (DP8 - plan management). */
@ExtendWith(MockitoExtension.class)
class DoseLogServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vilnius");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final long PLAN_ID = 10L;
    private static final OffsetDateTime MARKED = OffsetDateTime.parse("2026-11-16T09:00:00+02:00");

    @Mock
    private ConsumptionPlanRepository plans;

    @Mock
    private PlanEntryRepository entries;

    @Mock
    private PlanReminderRepository reminders;

    @Mock
    private DoseLogRepository logs;

    @Mock
    private SupplementRepository supplements;

    private DoseLogService service(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new DoseLogService(plans, entries, reminders, logs, supplements, new AdherenceProperties(50), clock);
    }

    private DoseLogService service() {
        return service(LocalDateTime.of(2026, 11, 16, 14, 0));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token").header("alg", "none").subject("google-1").build();
    }

    private static ConsumptionPlan plan(boolean active) {
        ConsumptionPlan plan = new ConsumptionPlan(1L, 2L, "model");
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        if (active) {
            plan.activate();
        }
        return plan;
    }

    private static PlanEntry entry(long id, String name, PlanEntry.DayPart part, LocalDate start, LocalDate end) {
        PlanEntry entry = new PlanEntry(PLAN_ID, 100 + id, 5L, name, new BigDecimal("15.0000"), "mg", part,
                PlanEntry.MealRelation.AFTER_MEAL, start, end, null, null);
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    private static DoseLog mark(long entryId, LocalDate day, DoseLog.Status status) {
        return new DoseLog(entryId, day, status, MARKED);
    }

    private void ownedPlan(ConsumptionPlan plan) {
        when(plans.findOwnedById(PLAN_ID, "google-1")).thenReturn(Optional.of(plan));
    }

    private static ResponseStatusException failure(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run);
    }

    // ---------------------------------------------------------------- schedule

    @Test
    void TA50_scheduleListsTodaysDosesInTimeOrderWithTheirState() {
        ownedPlan(plan(true));
        PlanEntry zinc = entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10));
        PlanEntry magnesium = entry(2L, "Magnis", PlanEntry.DayPart.NOON, TODAY.minusDays(1), TODAY.plusDays(10));
        PlanEntry vitamin = entry(3L, "Vitaminas D3", PlanEntry.DayPart.EVENING, TODAY.minusDays(1), TODAY.plusDays(10));
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(vitamin, magnesium, zinc));
        when(reminders.findByPlanEntryId(1L)).thenReturn(Optional.empty());
        when(reminders.findByPlanEntryId(2L)).thenReturn(Optional.of(new PlanReminder(2L, LocalTime.of(9, 30))));
        when(reminders.findByPlanEntryId(3L)).thenReturn(Optional.empty());
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(3L, 2L, 1L), TODAY, TODAY)).thenReturn(List.of(
                mark(1L, TODAY, DoseLog.Status.TAKEN), mark(2L, TODAY, DoseLog.Status.SKIPPED)));

        DaySchedule schedule = service().schedule(PLAN_ID, null, jwt());

        assertEquals(TODAY, schedule.date());
        assertEquals("ACTIVE", schedule.planStatus());
        assertEquals(List.of("Cinkas", "Magnis", "Vitaminas D3"),
                schedule.doses().stream().map(d -> d.supplementName()).toList());
        assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(9, 30), LocalTime.of(19, 0)),
                schedule.doses().stream().map(d -> d.time()).toList());
        assertEquals(List.of(DoseState.TAKEN, DoseState.SKIPPED, DoseState.PENDING),
                schedule.doses().stream().map(d -> d.state()).toList());
        assertEquals(List.of(true, true, false), schedule.doses().stream().map(d -> d.canCancel()).toList());
    }

    @Test
    void TA50_scheduleSkipsEntriesWhoseCycleDoesNotCoverTheDay() {
        ownedPlan(plan(true));
        PlanEntry finished = entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(20), TODAY.minusDays(1));
        PlanEntry notStarted = entry(2L, "Magnis", PlanEntry.DayPart.NOON, TODAY.plusDays(1), TODAY.plusDays(10));
        PlanEntry running = entry(3L, "Selenas", PlanEntry.DayPart.EVENING, TODAY, TODAY.plusDays(10));
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(finished, notStarted, running));
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(3L), TODAY, TODAY)).thenReturn(List.of());

        DaySchedule schedule = service().schedule(PLAN_ID, TODAY, jwt());

        assertEquals(1, schedule.doses().size());
        assertEquals("Selenas", schedule.doses().get(0).supplementName());
        assertEquals(DoseState.PENDING, schedule.doses().get(0).state());
    }

    @Test
    void TA50_scheduleOfDraftPlanHasNoDoses() {
        ownedPlan(plan(false));

        DaySchedule schedule = service().schedule(PLAN_ID, null, jwt());

        assertEquals("DRAFT", schedule.planStatus());
        assertTrue(schedule.doses().isEmpty());
        verifyNoInteractions(entries, logs);
    }

    @Test
    void TA69_marksOfEarlierDaysCannotBeCancelled() {
        ownedPlan(plan(true));
        LocalDate yesterday = TODAY.minusDays(1);
        PlanEntry zinc = entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(5), TODAY.plusDays(10));
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(zinc));
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), yesterday, yesterday))
                .thenReturn(List.of(mark(1L, yesterday, DoseLog.Status.TAKEN)));

        DaySchedule schedule = service().schedule(PLAN_ID, yesterday, jwt());

        assertEquals(DoseState.TAKEN, schedule.doses().get(0).state());
        assertFalse(schedule.doses().get(0).canCancel());
    }

    @Test
    void TA70_unknownOrForeignPlanIsNotFound() {
        when(plans.findOwnedById(PLAN_ID, "google-1")).thenReturn(Optional.empty());

        ResponseStatusException error = failure(() -> service().schedule(PLAN_ID, null, jwt()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    // ---------------------------------------------------------------- mark

    @Test
    void TA50_markTakenSavesDoseWithDateTimeAndStatus() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10))));
        when(logs.findByPlanEntryIdAndDoseDate(1L, TODAY)).thenReturn(Optional.empty());
        when(logs.save(any(DoseLog.class))).thenAnswer(i -> i.getArgument(0));

        MarkResponse response = service().mark(PLAN_ID, 1L, TODAY, DoseLog.Status.TAKEN, jwt());

        ArgumentCaptor<DoseLog> captor = ArgumentCaptor.forClass(DoseLog.class);
        verify(logs).save(captor.capture());
        DoseLog saved = captor.getValue();
        assertEquals(1L, saved.getPlanEntryId().longValue());
        assertEquals(TODAY, saved.getDoseDate());
        assertEquals(DoseLog.Status.TAKEN, saved.getStatus());
        assertEquals(LocalDateTime.of(2026, 11, 16, 14, 0).atZone(ZONE).toInstant(), saved.getMarkedAt().toInstant());
        assertEquals(DoseState.TAKEN, response.dose().state());
        assertTrue(response.dose().canCancel());
        assertNull(response.guidance(), "A taken dose needs no guidance");
        verifyNoInteractions(supplements);
    }

    @Test
    void TA50_markingAgainReplacesThePreviousMark() {
        ownedPlan(plan(true));
        DoseLog existing = mark(1L, TODAY, DoseLog.Status.TAKEN);
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10))));
        when(logs.findByPlanEntryIdAndDoseDate(1L, TODAY)).thenReturn(Optional.of(existing));
        when(logs.save(any(DoseLog.class))).thenAnswer(i -> i.getArgument(0));

        MarkResponse response = service().mark(PLAN_ID, 1L, TODAY, DoseLog.Status.SKIPPED, jwt());

        assertEquals(DoseLog.Status.SKIPPED, existing.getStatus());
        verify(logs).save(existing);
        assertEquals(DoseState.SKIPPED, response.dose().state());
    }

    @Test
    void TA66_skippedDoseComesWithGuidanceAndTheCatalogRule() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10))));
        when(logs.findByPlanEntryIdAndDoseDate(1L, TODAY)).thenReturn(Optional.empty());
        when(logs.save(any(DoseLog.class))).thenAnswer(i -> i.getArgument(0));
        when(supplements.findById(5L)).thenReturn(Optional.of(new Supplement("Cinkas", "Cinko citratas",
                SupplementForm.CAPSULE, new BigDecimal("15"), "mg", new BigDecimal("25"), "Nedvigubinti dozės")));

        MarkResponse response = service().mark(PLAN_ID, 1L, TODAY, DoseLog.Status.SKIPPED, jwt());

        assertNotNull(response.guidance());
        assertEquals("DRINK_NOW", response.guidance().action());
        assertTrue(response.guidance().message().contains("išgerti dabar"));
        assertEquals("Nedvigubinti dozės", response.guidance().supplementRule());
    }

    @Test
    void TA66_lateSkippedDoseTellsToSkipWithoutDoubling() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10))));
        when(logs.findByPlanEntryIdAndDoseDate(1L, TODAY)).thenReturn(Optional.empty());
        when(logs.save(any(DoseLog.class))).thenAnswer(i -> i.getArgument(0));

        MarkResponse response = service(LocalDateTime.of(2026, 11, 16, 21, 0))
                .mark(PLAN_ID, 1L, TODAY, DoseLog.Status.SKIPPED, jwt());

        assertEquals("SKIP", response.guidance().action());
        assertTrue(response.guidance().message().contains("negerkite dvigubos dozės"));
        assertNull(response.guidance().supplementRule(), "No rule when the catalog supplement has none");
    }

    @Test
    void TA70_onlyTodayCanBeMarked() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(5), TODAY.plusDays(10))));

        ResponseStatusException yesterday = failure(
                () -> service().mark(PLAN_ID, 1L, TODAY.minusDays(1), DoseLog.Status.TAKEN, jwt()));
        ResponseStatusException tomorrow = failure(
                () -> service().mark(PLAN_ID, 1L, TODAY.plusDays(1), DoseLog.Status.TAKEN, jwt()));

        assertEquals(HttpStatus.BAD_REQUEST, yesterday.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, tomorrow.getStatusCode());
        verify(logs, never()).save(any(DoseLog.class));
    }

    @Test
    void TA70_dosesCanOnlyBeMarkedInAnActivePlan() {
        ownedPlan(plan(false));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10))));

        ResponseStatusException error = failure(
                () -> service().mark(PLAN_ID, 1L, TODAY, DoseLog.Status.TAKEN, jwt()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(logs, never()).save(any(DoseLog.class));
    }

    @Test
    void TA70_dayOutsideTheCycleCannotBeMarked() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(20), TODAY.minusDays(1))));

        ResponseStatusException error = failure(
                () -> service().mark(PLAN_ID, 1L, TODAY, DoseLog.Status.TAKEN, jwt()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void TA70_unknownEntryIsNotFound() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(99L, PLAN_ID)).thenReturn(Optional.empty());

        ResponseStatusException error = failure(
                () -> service().mark(PLAN_ID, 99L, TODAY, DoseLog.Status.TAKEN, jwt()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void TA69_cancelRemovesTodaysMark() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10))));
        when(logs.deleteByPlanEntryIdAndDoseDate(1L, TODAY)).thenReturn(1L);

        service().cancel(PLAN_ID, 1L, TODAY, jwt());

        verify(logs).deleteByPlanEntryIdAndDoseDate(1L, TODAY);
    }

    @Test
    void TA69_cancelIsOnlyPossibleOnTheSameDay() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(5), TODAY.plusDays(10))));

        ResponseStatusException error = failure(() -> service().cancel(PLAN_ID, 1L, TODAY.minusDays(1), jwt()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(logs, never()).deleteByPlanEntryIdAndDoseDate(any(Long.class), any(LocalDate.class));
    }

    @Test
    void TA69_cancelWithoutMarkIsNotFound() {
        ownedPlan(plan(true));
        when(entries.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, TODAY.minusDays(1), TODAY.plusDays(10))));
        when(logs.deleteByPlanEntryIdAndDoseDate(1L, TODAY)).thenReturn(0L);

        ResponseStatusException error = failure(() -> service().cancel(PLAN_ID, 1L, TODAY, jwt()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    // ---------------------------------------------------------------- history

    @Test
    void TA67_historyCountsTakenSkippedAndUnmarkedDoses() {
        ownedPlan(plan(true));
        LocalDate start = LocalDate.of(2026, 11, 7);
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, start, LocalDate.of(2026, 11, 26))));
        List<DoseLog> marks = new java.util.ArrayList<>(IntStream.rangeClosed(7, 12)
                .mapToObj(day -> mark(1L, LocalDate.of(2026, 11, day), DoseLog.Status.TAKEN)).toList());
        marks.add(mark(1L, LocalDate.of(2026, 11, 13), DoseLog.Status.SKIPPED));
        marks.add(mark(1L, TODAY, DoseLog.Status.TAKEN));
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), start, TODAY)).thenReturn(marks);

        HistorySummary history = service().history(PLAN_ID, null, null, jwt());

        assertEquals(10, history.planned(), "9 judged days (7-15 Nov) plus today because it was marked");
        assertEquals(7, history.taken());
        assertEquals(1, history.skipped());
        assertEquals(2, history.unmarked());
        assertEquals(70, history.adherencePercent());
        assertFalse(history.cycleEnded());
        assertFalse(history.notFollowed());
        assertNull(history.notice());
        assertEquals(1, history.entries().size());
        assertEquals(70, history.entries().get(0).adherencePercent());
    }

    @Test
    void TA67_todaysUnmarkedDoseIsNotCountedAsMissed() {
        ownedPlan(plan(true));
        LocalDate start = LocalDate.of(2026, 11, 14);
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.EVENING, start, LocalDate.of(2026, 11, 30))));
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), start, TODAY)).thenReturn(List.of());

        HistorySummary history = service().history(PLAN_ID, null, null, jwt());

        assertEquals(2, history.planned(), "14 and 15 Nov only; the evening dose of today is still to come");
        assertEquals(0, history.taken());
        assertEquals(2, history.unmarked());
        assertEquals(0, history.adherencePercent());
    }

    @Test
    void TA51_planWasNotFollowedNoticeAppearsWhenTheCycleEndedWithTooManyMissedDoses() {
        ownedPlan(plan(true));
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 10);
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, start, end)));
        List<DoseLog> taken = IntStream.rangeClosed(1, 4)
                .mapToObj(day -> mark(1L, LocalDate.of(2026, 11, day), DoseLog.Status.TAKEN)).toList();
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), start, TODAY)).thenReturn(taken);
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), start, end)).thenReturn(taken);

        HistorySummary history = service().history(PLAN_ID, null, null, jwt());

        assertTrue(history.cycleEnded());
        assertTrue(history.notFollowed());
        assertNotNull(history.notice());
        assertTrue(history.notice().contains("50 %"));
        assertTrue(history.notice().contains("rezultatai gali būti nepasiekti"));
        assertEquals(10, history.planned());
        assertEquals(4, history.taken());
        assertEquals(6, history.unmarked());
        assertEquals(40, history.adherencePercent());
    }

    @Test
    void TA51_noNoticeWhenEnoughDosesWereTakenInAFinishedCycle() {
        ownedPlan(plan(true));
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 10);
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, start, end)));
        List<DoseLog> taken = IntStream.rangeClosed(1, 8)
                .mapToObj(day -> mark(1L, LocalDate.of(2026, 11, day), DoseLog.Status.TAKEN)).toList();
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), start, TODAY)).thenReturn(taken);
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), start, end)).thenReturn(taken);

        HistorySummary history = service().history(PLAN_ID, null, null, jwt());

        assertTrue(history.cycleEnded());
        assertFalse(history.notFollowed());
        assertNull(history.notice());
        assertEquals(80, history.adherencePercent());
    }

    @Test
    void TA51_noNoticeWhileTheCycleIsStillRunning() {
        ownedPlan(plan(true));
        LocalDate start = LocalDate.of(2026, 11, 10);
        when(entries.findByPlanIdOrderByIdAsc(PLAN_ID)).thenReturn(List.of(
                entry(1L, "Cinkas", PlanEntry.DayPart.MORNING, start, LocalDate.of(2026, 11, 30))));
        when(logs.findByPlanEntryIdInAndDoseDateBetween(List.of(1L), start, TODAY)).thenReturn(List.of());

        HistorySummary history = service().history(PLAN_ID, null, null, jwt());

        assertFalse(history.cycleEnded());
        assertFalse(history.notFollowed());
        assertNull(history.notice());
    }

    @Test
    void TA67_historyOfDraftPlanIsEmpty() {
        ownedPlan(plan(false));

        HistorySummary history = service().history(PLAN_ID, null, null, jwt());

        assertEquals(0, history.planned());
        assertNull(history.adherencePercent());
        assertTrue(history.entries().isEmpty());
        verifyNoInteractions(logs);
    }

    @Test
    void TA67_historyRejectsInvalidPeriods() {
        ownedPlan(plan(true));

        ResponseStatusException reversed = failure(() -> service().history(
                PLAN_ID, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), jwt()));
        ResponseStatusException tooLong = failure(() -> service().history(
                PLAN_ID, LocalDate.of(2025, 1, 1), TODAY, jwt()));

        assertEquals(HttpStatus.BAD_REQUEST, reversed.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, tooLong.getStatusCode());
    }
}