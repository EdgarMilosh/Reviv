package lt.reviv.service;

import lt.reviv.bloodtests.ai.plan.OpenAiPlanClient;
import lt.reviv.bloodtests.ai.plan.PlanProperties;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.dto.review.BloodTestDetailResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumptionPlanServiceTest {
    @Mock BloodTestReviewService review;
    @Mock RecommendationGenerationRepository generations;
    @Mock SupplementRecommendationRepository recommendations;
    @Mock BloodTestIndicatorRepository indicators;
    @Mock SupplementRepository supplements;
    @Mock SupplementInteractionRepository interactions;
    @Mock ConsumptionPlanRepository plans;
    @Mock PlanEntryRepository entries;
    @Mock PlanReminderRepository reminders;
    @Mock OpenAiPlanClient ai;

    ConsumptionPlanService service;
    Jwt jwt;

    @BeforeEach
    void setup() {
        service = new ConsumptionPlanService(review, generations, recommendations, indicators,
                supplements, interactions, plans, entries, reminders,
                new IndicatorDeviationCalculator(), ai,
                new PlanProperties(true, "gpt-6-luna", 3500), JsonMapper.builder().build());
        jwt = Jwt.withTokenValue("token").header("alg", "none").subject("test-user").build();
    }

    private void confirmed() {
        var details = mock(BloodTestDetailResponse.class);
        when(details.status()).thenReturn("CONFIRMED");
        when(review.getDetails(10L, jwt)).thenReturn(details);
    }

    private void generation() {
        var generation = new RecommendationGeneration(10L, "qwen3:14b", "{}", false);
        ReflectionTestUtils.setField(generation, "id", 99L);
        when(generations.findByBloodTestIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(List.of(generation));
    }

    private SupplementRecommendation suggestion() {
        return new SupplementRecommendation(99L, 11L, 12L, "Geležis",
                SupplementRecommendation.RecommendationStatus.SUGGESTED,
                new BigDecimal("15"), "mg", 30,
                "Tyrimo rezultatas žemiau intervalo", "[]", false);
    }

    @Test
    void rejectsUnconfirmedBloodTestBeforeCallingAi() {
        var details = mock(BloodTestDetailResponse.class);
        when(details.status()).thenReturn("NEEDS_REVIEW");
        when(review.getDetails(10L, jwt)).thenReturn(details);
        var error = assertThrows(ResponseStatusException.class, () -> service.generate(10L, jwt));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(generations, ai);
    }

    @Test
    void refusesGeneratingUntilAllSuggestedRecommendationsAreDecided() {
        confirmed(); generation();
        when(recommendations.findByGenerationIdOrderByIdAsc(99L)).thenReturn(List.of(suggestion()));
        var error = assertThrows(ResponseStatusException.class, () -> service.generate(10L, jwt));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(ai);
    }

    @Test
    void refusesWhenAllSuggestionsWereRejected() {
        confirmed(); generation();
        var item = suggestion();
        item.decide(SupplementRecommendation.Decision.REJECTED);
        when(recommendations.findByGenerationIdOrderByIdAsc(99L)).thenReturn(List.of(item));
        var error = assertThrows(ResponseStatusException.class, () -> service.generate(10L, jwt));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(ai);
    }

    @Test
    void refusesUnverifiedDoseAboveCatalogLimitBeforeCallingAi() {
        confirmed(); generation();
        var item = suggestion();
        item.decide(SupplementRecommendation.Decision.ACCEPTED);
        when(recommendations.findByGenerationIdOrderByIdAsc(99L)).thenReturn(List.of(item));
        var indicator = new BloodTestIndicator(10L, "Geležis", "5", "umol/L", "10-30");
        ReflectionTestUtils.setField(indicator, "id", 11L);
        when(indicators.findByBloodTestIdOrderByIdAsc(10L)).thenReturn(List.of(indicator));
        var supplement = new Supplement("Geležis", "Geležis", SupplementForm.TABLET,
                new BigDecimal("5"), "mg", new BigDecimal("10"), null);
        ReflectionTestUtils.setField(supplement, "id", 12L);
        when(supplements.findAllById(any())).thenReturn(List.of(supplement));

        var error = assertThrows(ResponseStatusException.class, () -> service.generate(10L, jwt));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(ai);
    }
    private SupplementRecommendation acceptedForIndicator(long indicatorId, long recommendationId, String dose) {
        var recommendation = new SupplementRecommendation(99L, indicatorId, 12L, "Geležis",
                SupplementRecommendation.RecommendationStatus.SUGGESTED,
                new BigDecimal(dose), "mg", 30, "Rodiklis žemiau intervalo", "[]", false);
        ReflectionTestUtils.setField(recommendation, "id", recommendationId);
        recommendation.decide(SupplementRecommendation.Decision.ACCEPTED);
        return recommendation;
    }

    private void mockFourIndicatorsAndSupplement() {
        List<BloodTestIndicator> results = new ArrayList<>();
        for (long id : new long[]{11L, 13L, 14L, 15L}) {
            var indicator = new BloodTestIndicator(10L, "Rodiklis " + id,
                    "5", "g/L", "10-30");
            ReflectionTestUtils.setField(indicator, "id", id);
            results.add(indicator);
        }
        when(indicators.findByBloodTestIdOrderByIdAsc(10L)).thenReturn(results);
        var supplement = new Supplement("Geležis", "Geležies bisglicinatas", SupplementForm.CAPSULE,
                new BigDecimal("14"), "mg", new BigDecimal("45"), null);
        ReflectionTestUtils.setField(supplement, "id", 12L);
        when(supplements.findAllById(any())).thenReturn(List.of(supplement));
    }

    @Test
    void sameSupplementForFourIndicatorsProducesOnePlanEntryAndAllIndicatorsReachAi() {
        confirmed(); generation(); mockFourIndicatorsAndSupplement();
        var accepted = List.of(acceptedForIndicator(11L, 101L, "14"),
                acceptedForIndicator(13L, 102L, "14"),
                acceptedForIndicator(14L, 103L, "14"),
                acceptedForIndicator(15L, 104L, "14"));
        when(recommendations.findByGenerationIdOrderByIdAsc(99L)).thenReturn(accepted);
        when(ai.suggest(anyList(), anyList())).thenReturn(List.of(new OpenAiPlanClient.Suggestion(
                12L, PlanEntry.DayPart.MORNING, PlanEntry.MealRelation.AFTER_MEAL, null, "Informacinis tvarkaraštis.")));
        when(plans.saveAndFlush(any(ConsumptionPlan.class))).thenAnswer(invocation -> {
            ConsumptionPlan plan = invocation.getArgument(0);
            ReflectionTestUtils.setField(plan, "id", 123L);
            return plan;
        });

        service.generate(10L, jwt);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OpenAiPlanClient.AcceptedInput>> aiCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(ai).suggest(aiCaptor.capture(), anyList());
        List<OpenAiPlanClient.AcceptedInput> aiInputs = aiCaptor.getValue();
        assertEquals(1, aiInputs.size());
        assertEquals(4, aiInputs.getFirst().indicators().size());
        assertEquals(new BigDecimal("14"), aiInputs.getFirst().dailyDose());

        ArgumentCaptor<PlanEntry> entryCaptor = ArgumentCaptor.forClass(PlanEntry.class);
        verify(entries, times(1)).save(entryCaptor.capture());
        assertEquals(Long.valueOf(12L), entryCaptor.getValue().getSupplementId());
        assertEquals(new BigDecimal("14"), entryCaptor.getValue().getDailyDose());
        assertTrue(entryCaptor.getValue().getNotes().contains("4 rodikliais"));
    }

    @Test
    void differingDosesForSameSupplementAreNotSilentlyCombined() {
        confirmed(); generation(); mockFourIndicatorsAndSupplement();
        when(recommendations.findByGenerationIdOrderByIdAsc(99L)).thenReturn(List.of(
                acceptedForIndicator(11L, 101L, "14"),
                acceptedForIndicator(13L, 102L, "28")));

        var error = assertThrows(ResponseStatusException.class, () -> service.generate(10L, jwt));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("skirtingomis dozėmis"));
        verifyNoInteractions(ai);
        verify(entries, never()).save(any());
    }
}
