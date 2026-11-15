package lt.reviv.service;

import lt.reviv.bloodtests.ai.rec.OllamaRecommendationClient;
import lt.reviv.bloodtests.service.rec.RecommendationContextService;
import lt.reviv.bloodtests.service.rec.RecommendationPersistenceService;
import lt.reviv.bloodtests.service.rec.RecommendationPreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** US-9 preview rules without Ollama or database connections. */
@ExtendWith(MockitoExtension.class)
class RecommendationPreviewServiceTest {
    @Mock RecommendationContextService contexts;
    @Mock OllamaRecommendationClient ollama;
    @Mock RecommendationPersistenceService persistence;
    RecommendationPreviewService service;
    Jwt jwt;

    @BeforeEach
    void init() {
        service = new RecommendationPreviewService(contexts, ollama, persistence);
        jwt = Jwt.withTokenValue("test").header("alg", "none").subject("demo").build();
    }

    private OllamaRecommendationClient.IndicatorInput indicator() {
        return new OllamaRecommendationClient.IndicatorInput(11L, "HGB", "106", "g/L", "120-155", "BELOW_RANGE");
    }

    private OllamaRecommendationClient.SupplementInput supplement(BigDecimal max) {
        return new OllamaRecommendationClient.SupplementInput(5L, "Geležis", "Bisglicinatas", "CAPSULE",
                new BigDecimal("14"), "mg", max);
    }

    private RecommendationContextService.RecommendationContext ctx(boolean critical, boolean notAssessed,
                                                                     boolean hasIndicators, boolean hasCatalog) {
        return new RecommendationContextService.RecommendationContext(
                hasIndicators ? List.of(indicator()) : List.of(),
                hasCatalog ? List.of(supplement(new BigDecimal("45"))) : List.of(),
                List.of(), new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of()),
                2, 1, critical, notAssessed);
    }

    private void given(RecommendationContextService.RecommendationContext c) {
        when(contexts.prepare(10L, jwt)).thenReturn(c);
    }

    private OllamaRecommendationClient.Proposal proposed(BigDecimal dose, String unit, Integer days) {
        return new OllamaRecommendationClient.Proposal("SUGGESTED", 11L, 5L,
                "Susiję su HGB", dose, unit, days, List.of(), false);
    }

    private RecommendationPreviewService.PreviewResponse runWith(OllamaRecommendationClient.Proposal proposal) {
        given(ctx(false, true, true, true));
        when(ollama.suggest(anyList(), anyList(), anyList(), any())).thenReturn(List.of(proposal));
        return service.generate(10L, jwt);
    }

    @Test
    void criticalDeviationStopsAiAndPersistence() {
        given(ctx(true, false, true, true));
        var result = service.generate(10L, jwt);
        assertTrue(result.criticalDetected());
        assertFalse(result.canAccept());
        assertTrue(result.recommendations().isEmpty());
        assertTrue(result.notices().stream().anyMatch(n -> n.contains("kritinis")));
        verifyNoInteractions(ollama, persistence);
    }

    @Test
    void noBelowRangeIndicatorsDoesNotCallOllama() {
        given(ctx(false, true, false, true));
        var result = service.generate(10L, jwt);
        assertTrue(result.criticalityNotAssessed());
        assertEquals(2, result.normalCount());
        assertEquals(1, result.undeterminedCount());
        assertTrue(result.notices().stream().anyMatch(n -> n.contains("Nėra rodiklių")));
        verifyNoInteractions(ollama, persistence);
    }

    @Test
    void emptyCatalogDoesNotCallOllama() {
        given(ctx(false, false, true, false));
        var result = service.generate(10L, jwt);
        assertTrue(result.notices().stream().anyMatch(n -> n.contains("katalogas tuščias")));
        verifyNoInteractions(ollama, persistence);
    }

    @Test
    void validProposalIncludesSupplementDoseDurationAndPersistsPreview() {
        var result = runWith(proposed(new BigDecimal("14"), "mg", 30));
        assertEquals(1, result.recommendations().size());
        var item = result.recommendations().getFirst();
        assertEquals(11L, item.indicatorId());
        assertEquals("HGB", item.indicatorName());
        assertEquals(5L, item.supplementId());
        assertEquals("Geležis", item.supplementName());
        assertEquals(new BigDecimal("14"), item.proposedDailyDose());
        assertEquals("mg", item.doseUnit());
        assertEquals(30, item.durationDays());
        assertTrue(item.warnings().stream().anyMatch(w -> w.contains("mediciniškai")));
        verify(persistence).save(eq(10L), any(), anyList(), same(result));
    }

    @Test
    void nonSuggestedResultAppearsAsNotRecommendedAndIncludesWarnings() {
        var proposal = new OllamaRecommendationClient.Proposal("NEEDS_REVIEW", 11L, null,
                "Trūksta tyrimų", null, null, null, List.of("Alergija", " ", "", "Sąveika"), true);
        var result = runWith(proposal);
        assertEquals(0, result.recommendations().size());
        assertEquals(1, result.notRecommended().size());
        var skipped = result.notRecommended().getFirst();
        assertEquals("HGB", skipped.indicatorName());
        assertEquals("NEEDS_REVIEW", skipped.status());
        assertEquals(3, skipped.warnings().size());
        assertTrue(skipped.warnings().stream().anyMatch(w -> w.contains("specialisto")));
        verify(persistence).save(eq(10L), any(), anyList(), same(result));
    }

    @Test
    void recommendationWithSpecialistFlagAndRiskWarningsIsPreserved() {
        var proposal = new OllamaRecommendationClient.Proposal("SUGGESTED", 11L, 5L,
                "Priežastis", new BigDecimal("12"), "mg", 45,
                List.of("Stebėti", "", " ", "Gali sąveikauti"), true);
        var result = runWith(proposal);
        var warnings = result.recommendations().getFirst().warnings();
        assertTrue(warnings.contains("Stebėti"));
        assertTrue(warnings.contains("Gali sąveikauti"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("specialisto")));
        assertEquals(45, result.recommendations().getFirst().durationDays());
    }

    @Test
    void unknownSupplementIdIsRejectedWithoutPersistence() {
        var bad = new OllamaRecommendationClient.Proposal("SUGGESTED", 11L, 999L,
                "?", BigDecimal.ONE, "mg", 30, List.of(), false);
        given(ctx(false, false, true, true));
        when(ollama.suggest(anyList(), anyList(), anyList(), any())).thenReturn(List.of(bad));
        assertThrows(IllegalStateException.class, () -> service.generate(10L, jwt));
        verifyNoInteractions(persistence);
    }

    @Test
    void rejectedDoseIsClearedForNonPositiveValue() {
        var item = runWith(proposed(BigDecimal.ZERO, "mg", 30)).recommendations().getFirst();
        assertNull(item.proposedDailyDose());
        assertNull(item.doseUnit());
        assertTrue(item.warnings().stream().anyMatch(w -> w.contains("Netinkama")));
    }

    @Test
    void rejectedDoseIsClearedForUnitMismatch() {
        var item = runWith(proposed(new BigDecimal("14"), "g", 30)).recommendations().getFirst();
        assertNull(item.proposedDailyDose());
        assertNull(item.doseUnit());
        assertTrue(item.warnings().stream().anyMatch(w -> w.contains("vienetas")));
    }

    @Test
    void rejectedDoseIsClearedForMissingUnit() {
        var item = runWith(proposed(new BigDecimal("14"), null, 30)).recommendations().getFirst();
        assertNull(item.proposedDailyDose());
        assertNull(item.doseUnit());
    }

    @Test
    void rejectsDoseAboveCatalogMaximum() {
        var item = runWith(proposed(new BigDecimal("46"), "mg", 30)).recommendations().getFirst();
        assertNull(item.proposedDailyDose());
        assertTrue(item.warnings().stream().anyMatch(w -> w.contains("viršija")));
    }

    @Test
    void rejectsDoseWithoutCatalogMaximum() {
        var context = new RecommendationContextService.RecommendationContext(
                List.of(indicator()), List.of(supplement(null)), List.of(),
                new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of()),
                0, 0, false, false);
        given(context);
        when(ollama.suggest(anyList(), anyList(), anyList(), any())).thenReturn(
                List.of(proposed(new BigDecimal("10"), "mg", 30)));
        var item = service.generate(10L, jwt).recommendations().getFirst();
        assertNull(item.proposedDailyDose());
        assertTrue(item.warnings().stream().anyMatch(w -> w.contains("maksimali")));
    }

    @Test
    void nullDoseAndDurationRemainUnspecified() {
        var item = runWith(proposed(null, null, null)).recommendations().getFirst();
        assertNull(item.proposedDailyDose());
        assertNull(item.doseUnit());
        assertNull(item.durationDays());
    }

    @Test
    void invalidDurationIsNotPassedToPlan() {
        var item = runWith(proposed(new BigDecimal("10"), "mg", 366)).recommendations().getFirst();
        assertNull(item.durationDays());
        assertTrue(item.warnings().stream().anyMatch(w -> w.contains("trukmė")));
    }

    @Test
    void emptyAiProposalsProduceNotice() {
        given(ctx(false, false, true, true));
        when(ollama.suggest(anyList(), anyList(), anyList(), any())).thenReturn(List.of());
        var result = service.generate(10L, jwt);
        assertTrue(result.notices().stream().anyMatch(n -> n.contains("nepateikė")));
        verify(persistence).save(eq(10L), any(), anyList(), same(result));
    }
}
