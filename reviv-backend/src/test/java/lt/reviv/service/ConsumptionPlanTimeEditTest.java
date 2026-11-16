package lt.reviv.service;

import lt.reviv.bloodtests.ai.plan.OpenAiPlanClient;
import lt.reviv.bloodtests.ai.plan.PlanProperties;
import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.domain.plan.PlanReminder;
import lt.reviv.bloodtests.domain.plan.PlanScheduleTimes;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.bloodtests.repository.plan.PlanReminderRepository;
import lt.reviv.bloodtests.repository.rec.RecommendationGenerationRepository;
import lt.reviv.bloodtests.repository.rec.SupplementRecommendationRepository;
import lt.reviv.bloodtests.service.analysis.IndicatorDeviationCalculator;
import lt.reviv.bloodtests.service.plan.ConsumptionPlanService;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;
import lt.reviv.supplements.domain.Supplement;
import lt.reviv.supplements.domain.SupplementForm;
import lt.reviv.supplements.repository.SupplementInteractionRepository;
import lt.reviv.supplements.repository.SupplementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsumptionPlanTimeEditTest {
    private final ConsumptionPlanRepository plans = mock(ConsumptionPlanRepository.class);
    private final PlanEntryRepository entries = mock(PlanEntryRepository.class);
    private final PlanReminderRepository reminders = mock(PlanReminderRepository.class);
    private final SupplementRepository supplements = mock(SupplementRepository.class);
    private final SupplementInteractionRepository interactions = mock(SupplementInteractionRepository.class);
    private final SupplementRecommendationRepository recommendations = mock(SupplementRecommendationRepository.class);
    private final ConsumptionPlanService service = new ConsumptionPlanService(
            mock(BloodTestReviewService.class), mock(RecommendationGenerationRepository.class),
            recommendations, mock(BloodTestIndicatorRepository.class), supplements,
            interactions, plans, entries, reminders, new IndicatorDeviationCalculator(),
            mock(OpenAiPlanClient.class), new PlanProperties(true, "gpt-6-luna", 6500),
            JsonMapper.builder().build());
    private final Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("user-1").build();

    private ConsumptionPlan plan(boolean active) {
        ConsumptionPlan plan = new ConsumptionPlan(10L, 99L, "gpt-6-luna");
        ReflectionTestUtils.setField(plan, "id", 2L);
        if (active) plan.activate();
        when(plans.findOwnedById(2L, "user-1")).thenReturn(Optional.of(plan));
        return plan;
    }

    private PlanEntry entry() {
        PlanEntry entry = new PlanEntry(2L, 101L, 12L, "Geležis", new BigDecimal("14"),
                "mg", PlanEntry.DayPart.MORNING, PlanEntry.MealRelation.WITH_MEAL,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), null, "");
        ReflectionTestUtils.setField(entry, "id", 7L);
        when(entries.findByIdAndPlanId(7L, 2L)).thenReturn(Optional.of(entry));
        Supplement supplement = new Supplement("Geležis", "Geležies bisglicinatas", SupplementForm.CAPSULE,
                new BigDecimal("14"), "mg", new BigDecimal("45"), null);
        ReflectionTestUtils.setField(supplement, "id", 12L);
        when(supplements.findById(12L)).thenReturn(Optional.of(supplement));
        return entry;
    }

    private ConsumptionPlanService.EditRequest edit(PlanEntry.DayPart dayPart, LocalTime time) {
        return new ConsumptionPlanService.EditRequest(dayPart, time, PlanEntry.MealRelation.WITH_MEAL,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), null);
    }

    @Test
    void allowedTimesAreExactlyOneHourAroundDefault() {
        assertEquals(List.of(LocalTime.of(7, 0), LocalTime.of(8, 0), LocalTime.of(9, 0)),
                PlanScheduleTimes.allowedFor(PlanEntry.DayPart.MORNING));
        assertEquals(List.of(LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0)),
                PlanScheduleTimes.allowedFor(PlanEntry.DayPart.NOON));
        assertEquals(List.of(LocalTime.of(18, 0), LocalTime.of(19, 0), LocalTime.of(20, 0)),
                PlanScheduleTimes.allowedFor(PlanEntry.DayPart.EVENING));
        assertFalse(PlanScheduleTimes.isAllowed(PlanEntry.DayPart.MORNING, LocalTime.of(9, 30)));
        assertFalse(PlanScheduleTimes.isAllowed(PlanEntry.DayPart.MORNING, LocalTime.of(10, 0)));
    }

    @Test
    void rejectsScheduleOutsideOneHourWindow() {
        plan(false);
        entry();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateByPlanId(2L, 7L, edit(PlanEntry.DayPart.MORNING, LocalTime.of(10, 0)), jwt));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(entries, never()).save(any(PlanEntry.class));
    }

    @Test
    void draftUpdatesTimeWithoutChangingRecommendationDose() {
        plan(false);
        PlanEntry item = entry();
        when(entries.findByPlanIdOrderByIdAsc(2L)).thenReturn(List.of(item));
        var updated = service.updateByPlanId(2L, 7L,
                edit(PlanEntry.DayPart.NOON, LocalTime.of(12, 0)), jwt);
        assertEquals(LocalTime.of(12, 0), item.getScheduledTime());
        assertEquals(new BigDecimal("14"), item.getDailyDose());
        assertEquals(LocalTime.of(12, 0), updated.entries().getFirst().scheduledTime());
        verifyNoInteractions(reminders);
    }

    @Test
    void activePlanEditReschedulesExistingReminder() {
        plan(true);
        PlanEntry item = entry();
        when(entries.findByPlanIdOrderByIdAsc(2L)).thenReturn(List.of(item));
        PlanReminder reminder = new PlanReminder(7L, LocalTime.of(8, 0));
        when(reminders.findByPlanEntryId(7L)).thenReturn(Optional.of(reminder));

        service.updateByPlanId(2L, 7L, edit(PlanEntry.DayPart.MORNING, LocalTime.of(9, 0)), jwt);
        assertEquals(LocalTime.of(9, 0), item.getScheduledTime());
        assertEquals(LocalTime.of(9, 0), reminder.getReminderTime());
        assertEquals(new BigDecimal("14"), item.getDailyDose());
        verify(reminders).save(reminder);
    }
}
