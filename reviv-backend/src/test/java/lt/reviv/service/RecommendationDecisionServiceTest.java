package lt.reviv.service;

import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.repository.rec.RecommendationGenerationRepository;
import lt.reviv.bloodtests.repository.rec.SupplementRecommendationRepository;
import lt.reviv.bloodtests.service.rec.RecommendationDecisionService;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;
import lt.reviv.bloodtests.dto.review.BloodTestDetailResponse;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static lt.reviv.bloodtests.domain.rec.SupplementRecommendation.Decision.*;
import static lt.reviv.bloodtests.domain.rec.SupplementRecommendation.RecommendationStatus.*;

@ExtendWith(MockitoExtension.class)
class RecommendationDecisionServiceTest {
    private static final Long TEST_ID = 42L;
    private static final Long GENERATION_ID = 100L;
    private static final Long ITEM_ID = 200L;

    @Mock BloodTestReviewService reviewService;
    @Mock RecommendationGenerationRepository generationRepository;
    @Mock SupplementRecommendationRepository recommendationRepository;

    RecommendationDecisionService service;
    Jwt jwt;

    @BeforeEach
    void setup() {
        service = new RecommendationDecisionService(reviewService,
                generationRepository, recommendationRepository,
                JsonMapper.builder().build());
        jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("user-test")
                .build();
    }

    private void confirmed() {
        var details = mock(BloodTestDetailResponse.class);
        when(details.status()).thenReturn("CONFIRMED");
        when(reviewService.getDetails(TEST_ID, jwt)).thenReturn(details);
    }

    private void unconfirmed() {
        var details = mock(BloodTestDetailResponse.class);
        when(details.status()).thenReturn("NEEDS_REVIEW");
        when(reviewService.getDetails(TEST_ID, jwt)).thenReturn(details);
    }

    private void generationExists() {
        var generation = new RecommendationGeneration(TEST_ID, "qwen3:14b", "{}", true);
        ReflectionTestUtils.setField(generation, "id", GENERATION_ID);
        when(generationRepository.findByBloodTestIdOrderByCreatedAtDescIdDesc(TEST_ID))
                .thenReturn(List.of(generation));
    }

    private SupplementRecommendation suggestion(String warningsJson) {
        var item = new SupplementRecommendation(
                GENERATION_ID, 10L, 5L, "Geležis", SUGGESTED,
                new BigDecimal("14"), "mg", 30,
                "Sumažėjęs rodiklis", warningsJson, false);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    @Test
    void latestReturnsStoredItemsWithoutCallingOllama() {
        confirmed();
        generationExists();
        when(recommendationRepository.findByGenerationIdOrderByIdAsc(GENERATION_ID))
                .thenReturn(List.of(suggestion("[\"Patikrinkite dozę\"]")));

        var result = service.latest(TEST_ID, jwt);

        assertEquals(GENERATION_ID, result.generationId());
        assertEquals(TEST_ID, result.bloodTestId());
        assertTrue(result.criticalityNotAssessed());
        assertFalse(result.medicallyVerified());
        assertEquals(1, result.items().size());
        assertEquals("Geležis", result.items().getFirst().supplementName());
        assertEquals("PENDING", result.items().getFirst().decision());
        assertEquals(List.of("Patikrinkite dozę"), result.items().getFirst().warnings());
    }

    @Test
    void latestHandlesMissingWarnings() {
        confirmed(); generationExists();
        when(recommendationRepository.findByGenerationIdOrderByIdAsc(GENERATION_ID))
                .thenReturn(List.of(suggestion(null), suggestion("  ")));
        var result = service.latest(TEST_ID, jwt);
        assertTrue(result.items().get(0).warnings().isEmpty());
        assertTrue(result.items().get(1).warnings().isEmpty());
    }

    @Test
    void latestHandlesJsonNullAsEmptyWarningList() {
        confirmed(); generationExists();
        when(recommendationRepository.findByGenerationIdOrderByIdAsc(GENERATION_ID))
                .thenReturn(List.of(suggestion("null")));
        assertEquals(List.of(), service.latest(TEST_ID, jwt).items().getFirst().warnings());
    }

    @Test
    void corruptedWarningsJsonFailsLoudly() {
        confirmed(); generationExists();
        when(recommendationRepository.findByGenerationIdOrderByIdAsc(GENERATION_ID))
                .thenReturn(List.of(suggestion("[not JSON")));
        assertThrows(IllegalStateException.class, () -> service.latest(TEST_ID, jwt));
    }

    @Test
    void ownerGuardIsAlwaysInvokedAndPropagatesNotFound() {
        when(reviewService.getDetails(TEST_ID, jwt))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.latest(TEST_ID, jwt));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(generationRepository, recommendationRepository);
    }

    @Test
    void rejectsUnconfirmedTest() {
        unconfirmed();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.latest(TEST_ID, jwt));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(generationRepository, recommendationRepository);
    }

    @Test
    void latestNotFoundWhenNeverGenerated() {
        confirmed();
        when(generationRepository.findByBloodTestIdOrderByCreatedAtDescIdDesc(TEST_ID))
                .thenReturn(List.of());
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.latest(TEST_ID, jwt));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void acceptsSuggestedPendingRecommendation() {
        confirmed(); generationExists();
        var item = suggestion("[]");
        when(recommendationRepository.findByIdAndGenerationId(ITEM_ID, GENERATION_ID))
                .thenReturn(Optional.of(item));
        when(recommendationRepository.saveAndFlush(item)).thenReturn(item);

        var result = service.decide(TEST_ID, ITEM_ID, ACCEPTED, jwt);

        assertEquals("ACCEPTED", result.decision());
        assertNotNull(result.decidedAt());
        assertEquals(ACCEPTED, item.getDecision());
        verify(recommendationRepository).saveAndFlush(item);
    }

    @Test
    void rejectsSuggestedPendingRecommendation() {
        confirmed(); generationExists();
        var item = suggestion("[]");
        when(recommendationRepository.findByIdAndGenerationId(ITEM_ID, GENERATION_ID))
                .thenReturn(Optional.of(item));

        var result = service.decide(TEST_ID, ITEM_ID, REJECTED, jwt);

        assertEquals("REJECTED", result.decision());
        assertNotNull(result.decidedAt());
        verify(recommendationRepository).saveAndFlush(item);
    }

    @Test
    void doesNotAcceptPendingOrNullDecision() {
        confirmed();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.decide(TEST_ID, ITEM_ID, PENDING, jwt)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.decide(TEST_ID, ITEM_ID, null, jwt)).getStatusCode());
        verifyNoInteractions(generationRepository, recommendationRepository);
    }

    @Test
    void decisionNotFoundOutsideLatestGeneration() {
        confirmed(); generationExists();
        when(recommendationRepository.findByIdAndGenerationId(ITEM_ID, GENERATION_ID))
                .thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(TEST_ID, ITEM_ID, ACCEPTED, jwt));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void decisionRejectsAlreadyAccepted() {
        confirmed(); generationExists();
        var item = suggestion("[]");
        item.decide(ACCEPTED);
        when(recommendationRepository.findByIdAndGenerationId(ITEM_ID, GENERATION_ID))
                .thenReturn(Optional.of(item));
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(TEST_ID, ITEM_ID, REJECTED, jwt));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(recommendationRepository, never()).saveAndFlush(any());
    }

    @Test
    void decisionRejectsNotRecommendedItem() {
        confirmed(); generationExists();
        var item = new SupplementRecommendation(GENERATION_ID, 10L,
                null, null, NOT_RECOMMENDED, null, null, null,
                "Netinka", "[]", false);
        when(recommendationRepository.findByIdAndGenerationId(ITEM_ID, GENERATION_ID))
                .thenReturn(Optional.of(item));
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(TEST_ID, ITEM_ID, ACCEPTED, jwt));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(recommendationRepository, never()).saveAndFlush(any());
    }

    @Test
    void decisionRejectsNeedsReviewItem() {
        confirmed(); generationExists();
        var item = new SupplementRecommendation(GENERATION_ID, 10L,
                null, null, NEEDS_REVIEW, null, null, null,
                "Reikia peržiūros", "[]", true);
        when(recommendationRepository.findByIdAndGenerationId(ITEM_ID, GENERATION_ID))
                .thenReturn(Optional.of(item));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.decide(TEST_ID, ITEM_ID, ACCEPTED, jwt)).getStatusCode());
        verify(recommendationRepository, never()).saveAndFlush(any());
    }
}
