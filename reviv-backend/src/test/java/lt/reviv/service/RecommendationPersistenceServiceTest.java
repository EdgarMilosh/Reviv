package lt.reviv.service;

import lt.reviv.bloodtests.ai.rec.OllamaProperties;
import lt.reviv.bloodtests.ai.rec.OllamaRecommendationClient;
import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.repository.rec.RecommendationGenerationRepository;
import lt.reviv.bloodtests.repository.rec.SupplementRecommendationRepository;
import lt.reviv.bloodtests.service.rec.RecommendationContextService;
import lt.reviv.bloodtests.service.rec.RecommendationPersistenceService;
import lt.reviv.bloodtests.service.rec.RecommendationPreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static lt.reviv.bloodtests.domain.rec.SupplementRecommendation.RecommendationStatus.*;

@ExtendWith(MockitoExtension.class)
class RecommendationPersistenceServiceTest {
    @Mock RecommendationGenerationRepository generations;
    @Mock SupplementRecommendationRepository recommendations;
    @Mock OllamaProperties properties;

    RecommendationPersistenceService service;

    @BeforeEach
    void setup() {
        service = new RecommendationPersistenceService(generations, recommendations,
                properties, JsonMapper.builder().build());
    }

    private RecommendationContextService.RecommendationContext context(boolean critical) {
        var context = mock(RecommendationContextService.RecommendationContext.class);
        when(context.criticalDetected()).thenReturn(critical);
        return context;
    }

    private OllamaRecommendationClient.Proposal proposal(String status, long indicatorId) {
        return new OllamaRecommendationClient.Proposal(status, indicatorId,
                "SUGGESTED".equals(status) ? 5L : null,
                "Priežastis", "SUGGESTED".equals(status) ? new BigDecimal("14") : null,
                "SUGGESTED".equals(status) ? "mg" : null,
                "SUGGESTED".equals(status) ? 30 : null,
                List.of("Patikrinkite dozę"), "NEEDS_REVIEW".equals(status));
    }

    private RecommendationPreviewService.RecommendationItem suggestedItem(long indicatorId) {
        var item = mock(RecommendationPreviewService.RecommendationItem.class);
        when(item.indicatorId()).thenReturn(indicatorId);
        lenient().when(item.supplementId()).thenReturn(5L);
        lenient().when(item.supplementName()).thenReturn("Geležis");
        lenient().when(item.proposedDailyDose()).thenReturn(new BigDecimal("14"));
        lenient().when(item.doseUnit()).thenReturn("mg");
        lenient().when(item.durationDays()).thenReturn(30);
        lenient().when(item.reason()).thenReturn("Priežastis");
        lenient().when(item.warnings()).thenReturn(List.of("Patikrinkite dozę"));
        return item;
    }

    private RecommendationPreviewService.NotRecommendedItem notRecommendedItem(long id, String status) {
        var item = mock(RecommendationPreviewService.NotRecommendedItem.class);
        when(item.indicatorId()).thenReturn(id);
        lenient().when(item.status()).thenReturn(status);
        lenient().when(item.reason()).thenReturn("Neatitinka kriterijų");
        lenient().when(item.warnings()).thenReturn(List.of("Kreipkitės į specialistą"));
        return item;
    }

    private RecommendationPreviewService.PreviewResponse preview(
            List<RecommendationPreviewService.RecommendationItem> suggested,
            List<RecommendationPreviewService.NotRecommendedItem> notSuggested) {
        var preview = mock(RecommendationPreviewService.PreviewResponse.class);
        when(preview.recommendations()).thenReturn(suggested);
        when(preview.notRecommended()).thenReturn(notSuggested);
        return preview;
    }

    private void saveGeneration() {
        when(properties.model()).thenReturn("qwen3:14b");
        when(generations.save(any(RecommendationGeneration.class))).thenAnswer(invocation -> {
            RecommendationGeneration generation = invocation.getArgument(0);
            ReflectionTestUtils.setField(generation, "id", 100L);
            return generation;
        });
    }

    @Test
    void doesNotPersistCriticalRecommendations() {
        var context = context(true);
        assertNull(service.save(1L, context, List.of(proposal("SUGGESTED", 10)),
                mock(RecommendationPreviewService.PreviewResponse.class)));
        verifyNoInteractions(generations, recommendations);
    }

    @Test
    void doesNotPersistNullProposals() {
        var context = context(false);
        assertNull(service.save(1L, context, null,
                mock(RecommendationPreviewService.PreviewResponse.class)));
        verifyNoInteractions(generations, recommendations);
    }

    @Test
    void doesNotPersistEmptyProposals() {
        var context = context(false);
        assertNull(service.save(1L, context, List.of(),
                mock(RecommendationPreviewService.PreviewResponse.class)));
        verifyNoInteractions(generations, recommendations);
    }

    @Test
    void storesGenerationAndSuggestedRecommendation() {
        var context = context(false);
        var profile = new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of());
        when(context.profile()).thenReturn(profile);
        when(context.criticalityNotAssessed()).thenReturn(true);
        saveGeneration();

        Long id = service.save(1L, context, List.of(proposal("SUGGESTED", 10)),
                preview(List.of(suggestedItem(10)), List.of()));

        assertEquals(100L, id);
        var generationCaptor = ArgumentCaptor.forClass(RecommendationGeneration.class);
        verify(generations).save(generationCaptor.capture());
        assertEquals(1L, generationCaptor.getValue().getBloodTestId());
        assertEquals("qwen3:14b", generationCaptor.getValue().getAiModel());
        assertTrue(generationCaptor.getValue().isCriticalityNotAssessed());
        assertTrue(generationCaptor.getValue().getProfileSnapshotJson().contains("22"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SupplementRecommendation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(recommendations).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        var item = captor.getValue().getFirst();
        assertEquals(SUGGESTED, item.getRecommendationStatus());
        assertEquals(5L, item.getSupplementId());
        assertEquals(new BigDecimal("14"), item.getProposedDailyDose());
        assertEquals(100L, item.getGenerationId());
        assertEquals(SupplementRecommendation.Decision.PENDING, item.getDecision());
        assertTrue(item.getRiskWarningsJson().contains("Patikrinkite"));
    }

    @Test
    void sameSupplementForDifferentIndicatorsRemainsTwoDecisionsForUs11() {
        var context = context(false);
        when(context.profile()).thenReturn(new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of()));
        saveGeneration();
        var result = service.save(1L, context,
                List.of(proposal("SUGGESTED", 10), proposal("SUGGESTED", 11)),
                preview(List.of(suggestedItem(10), suggestedItem(11)), List.of()));
        assertEquals(100L, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SupplementRecommendation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(recommendations).saveAll(captor.capture());
        var records = captor.getValue();
        assertEquals(2, records.size());
        assertEquals(5L, records.get(0).getSupplementId());
        assertEquals(5L, records.get(1).getSupplementId());
        assertNotEquals(records.get(0).getIndicatorId(), records.get(1).getIndicatorId());
    }

    @Test
    void storesBothNotRecommendedAndNeedsReview() {
        var context = context(false);
        when(context.profile()).thenReturn(new OllamaRecommendationClient.ProfileInput(null, List.of(), List.of()));
        saveGeneration();
        var proposals = List.of(proposal("NOT_RECOMMENDED", 10), proposal("NEEDS_REVIEW", 11));
        var preview = preview(List.of(), List.of(
                notRecommendedItem(10, "NOT_RECOMMENDED"),
                notRecommendedItem(11, "NEEDS_REVIEW")));

        assertEquals(100L, service.save(1L, context, proposals, preview));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SupplementRecommendation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(recommendations).saveAll(captor.capture());
        var items = captor.getValue();
        assertEquals(NOT_RECOMMENDED, items.get(0).getRecommendationStatus());
        assertEquals(NEEDS_REVIEW, items.get(1).getRecommendationStatus());
        assertNull(items.get(0).getSupplementId());
        assertNull(items.get(1).getProposedDailyDose());
        assertTrue(items.get(1).isNeedsSpecialistReview());
    }

    @Test
    void rejectsMismatchedProposalCountBeforeCreatingGeneration() {
        var context = context(false);
        var preview = preview(List.of(), List.of());
        assertThrows(IllegalStateException.class,
                () -> service.save(1L, context, List.of(proposal("SUGGESTED", 10)), preview));
        verifyNoInteractions(generations, recommendations);
    }

    @Test
    void rejectsSuggestionNotPresentInPreview() {
        var context = context(false);
        var mismatched = suggestedItem(77);
        // Stubs used only after generation is saved.
        when(context.profile()).thenReturn(new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of()));
        saveGeneration();
        assertThrows(IllegalStateException.class,
                () -> service.save(1L, context, List.of(proposal("SUGGESTED", 10)),
                        preview(List.of(mismatched), List.of())));
        verify(recommendations, never()).saveAll(any());
    }

    @Test
    void rejectsNotRecommendedStatusMismatchedWithPreview() {
        var context = context(false);
        when(context.profile()).thenReturn(new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of()));
        saveGeneration();
        assertThrows(IllegalStateException.class,
                () -> service.save(1L, context, List.of(proposal("NEEDS_REVIEW", 10)),
                        preview(List.of(), List.of(notRecommendedItem(10, "NOT_RECOMMENDED")))));
        verify(recommendations, never()).saveAll(any());
    }

    @Test
    void invalidAiDecisionStatusIsRejected() {
        var context = context(false);
        when(context.profile()).thenReturn(new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of()));
        saveGeneration();
        assertThrows(IllegalArgumentException.class,
                () -> service.save(1L, context, List.of(proposal("INVALID", 10)),
                        preview(List.of(), List.of(notRecommendedItem(10, "NOT_RECOMMENDED")))));
        verify(recommendations, never()).saveAll(any());
    }
}
