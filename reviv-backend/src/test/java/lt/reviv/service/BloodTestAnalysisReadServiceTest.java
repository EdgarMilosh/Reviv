
package lt.reviv.service;

import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.domain.IndicatorExplanation;
import lt.reviv.bloodtests.dto.review.BloodTestDetailResponse;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.IndicatorExplanationRepository;
import lt.reviv.bloodtests.service.analysis.*;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodTestAnalysisReadServiceTest {

    private static final Long TEST_ID = 5L;

    @Mock
    private BloodTestReviewService reviewService;

    @Mock
    private BloodTestAnalysisService analysisService;

    @Mock
    private BloodTestAnalysisRepository analysisRepository;

    @Mock
    private IndicatorExplanationRepository explanationRepository;

    private BloodTestAnalysisReadService service;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        service = new BloodTestAnalysisReadService(
                reviewService,
                analysisService,
                analysisRepository,
                explanationRepository,
                JsonMapper.builder().build()
        );

        jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .build();
    }

    private void mockConfirmedTest() {
        BloodTestDetailResponse details =
                mock(BloodTestDetailResponse.class);

        when(details.status()).thenReturn("CONFIRMED");
        when(reviewService.getDetails(TEST_ID, jwt))
                .thenReturn(details);
    }

    private void mockPrepared(
            List<BloodTestAnalysisService.AnalyzedIndicator> critical,
            List<BloodTestAnalysisService.AnalyzedIndicator> forAi,
            List<BloodTestAnalysisService.AnalyzedIndicator> unknown
    ) {
        var prepared = mock(
                BloodTestAnalysisService.PreparedAnalysis.class
        );

        when(prepared.critical()).thenReturn(critical);
        when(prepared.forAi()).thenReturn(forAi);
        when(prepared.unknown()).thenReturn(unknown);

        when(analysisService.prepare(TEST_ID))
                .thenReturn(prepared);
    }

    private BloodTestAnalysisService.AnalyzedIndicator
    mockIndicator(Long indicatorId) {

        var indicator = mock(
                BloodTestAnalysisService.AnalyzedIndicator.class
        );

        when(indicator.indicatorId()).thenReturn(indicatorId);
        when(indicator.name()).thenReturn("HGB");
        when(indicator.rawValue()).thenReturn("104");
        when(indicator.unit()).thenReturn("g/l");
        when(indicator.referenceInterval()).thenReturn("117-153");

        when(indicator.status()).thenReturn(
                IndicatorDeviationCalculator.Status.BELOW
        );

        when(indicator.criticalStatus()).thenReturn(
                CriticalIndicatorService.CriticalStatus.NOT_ASSESSED
        );

        return indicator;
    }

    private void mockCompletedAnalysis() {
        BloodTestAnalysis analysis = mock(BloodTestAnalysis.class);

        when(analysis.getStatus()).thenReturn(
                BloodTestAnalysis.Status.COMPLETED
        );
        when(analysis.getId()).thenReturn(10L);

        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.of(analysis));
    }

    @Test
    void rejectsUnconfirmedBloodTest() {
        BloodTestDetailResponse details =
                mock(BloodTestDetailResponse.class);

        when(details.status()).thenReturn("PROCESSING");

        when(reviewService.getDetails(TEST_ID, jwt))
                .thenReturn(details);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getAnalysis(TEST_ID, jwt)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                analysisRepository,
                explanationRepository,
                analysisService
        );
    }

    @Test
    void returnsNotRequestedWhenAnalysisDoesNotExist() {
        mockConfirmedTest();

        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.empty());

        mockPrepared(List.of(), List.of(), List.of());

        var result = service.getAnalysis(TEST_ID, jwt);

        assertEquals("NOT_REQUESTED", result.status());
        assertTrue(result.indicators().isEmpty());

        verifyNoInteractions(explanationRepository);
    }

    @Test
    void returnsQueuedAnalysisWithoutExplanations() {
        mockConfirmedTest();

        BloodTestAnalysis analysis = mock(BloodTestAnalysis.class);

        when(analysis.getStatus()).thenReturn(
                BloodTestAnalysis.Status.QUEUED
        );

        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.of(analysis));

        mockPrepared(
                List.of(),
                List.of(mockIndicator(21L)),
                List.of()
        );

        var result = service.getAnalysis(TEST_ID, jwt);

        assertEquals("QUEUED", result.status());
        assertEquals(1, result.indicators().size());

        var indicator = result.indicators().get(0);

        assertEquals(21L, indicator.indicatorId());
        assertNull(indicator.meaning());
        assertTrue(indicator.possibleCauses().isEmpty());

        verifyNoInteractions(explanationRepository);
    }

    @Test
    void returnsCompletedAnalysisWithSavedExplanations() {
        mockConfirmedTest();
        mockCompletedAnalysis();

        IndicatorExplanation explanation =
                mock(IndicatorExplanation.class);

        when(explanation.getIndicatorId()).thenReturn(21L);
        when(explanation.getMeaning())
                .thenReturn("Sumažėjęs hemoglobino kiekis.");

        when(explanation.getPossibleCausesJson())
                .thenReturn(
                        "[\"Geležies trūkumas\", 123, \"Kraujo netekimas\"]"
                );

        when(explanation.getPracticalAdvice())
                .thenReturn("Aptarti rezultatus su gydytoju.");

        when(explanationRepository.findByAnalysisIdOrderByIdAsc(10L))
                .thenReturn(List.of(explanation));

        mockPrepared(
                List.of(),
                List.of(mockIndicator(21L)),
                List.of()
        );

        var result = service.getAnalysis(TEST_ID, jwt);

        assertEquals("COMPLETED", result.status());

        var indicator = result.indicators().get(0);

        assertEquals(
                "Sumažėjęs hemoglobino kiekis.",
                indicator.meaning()
        );

        assertEquals(
                List.of("Geležies trūkumas", "Kraujo netekimas"),
                indicator.possibleCauses()
        );

        assertEquals(
                "Aptarti rezultatus su gydytoju.",
                indicator.practicalAdvice()
        );
    }

    @Test
    void rejectsInvalidCausesJsonStructure() {
        mockConfirmedTest();
        mockCompletedAnalysis();

        IndicatorExplanation explanation =
                mock(IndicatorExplanation.class);

        when(explanation.getIndicatorId()).thenReturn(21L);
        when(explanation.getPossibleCausesJson())
                .thenReturn("{\"cause\":\"example\"}");

        when(explanationRepository.findByAnalysisIdOrderByIdAsc(10L))
                .thenReturn(List.of(explanation));

        mockPrepared(
                List.of(),
                List.of(mockIndicator(21L)),
                List.of()
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.getAnalysis(TEST_ID, jwt)
        );

        assertTrue(
                exception.getMessage().contains("JSON formatas")
        );
    }

    @Test
    void rejectsMalformedCausesJson() {
        mockConfirmedTest();
        mockCompletedAnalysis();

        IndicatorExplanation explanation =
                mock(IndicatorExplanation.class);

        when(explanation.getIndicatorId()).thenReturn(21L);
        when(explanation.getPossibleCausesJson())
                .thenReturn("[\"neužbaigtas");

        when(explanationRepository.findByAnalysisIdOrderByIdAsc(10L))
                .thenReturn(List.of(explanation));

        mockPrepared(
                List.of(),
                List.of(mockIndicator(21L)),
                List.of()
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.getAnalysis(TEST_ID, jwt)
        );

        assertTrue(
                exception.getMessage().contains(
                        "Nepavyko nuskaityti DI paaiskinimo"
                )
        );
    }
}
