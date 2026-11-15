package lt.reviv.service;

import lt.reviv.bloodtests.ai.analysis.OpenAiAnalysisClient;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.domain.IndicatorExplanation;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.IndicatorExplanationRepository;
import lt.reviv.bloodtests.service.analysis.AnalysisPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisPersistenceServiceTest {
    private static final Long ID = 31L;

    @Mock BloodTestAnalysisRepository analyses;
    @Mock IndicatorExplanationRepository explanations;
    private AnalysisPersistenceService service;

    @BeforeEach
    void setup() {
        service = new AnalysisPersistenceService(analyses, explanations,
                JsonMapper.builder().build());
    }

    private BloodTestAnalysis processing() {
        BloodTestAnalysis a = new BloodTestAnalysis(ID);
        a.markProcessing("gpt-test", "{}");
        when(analyses.findByBloodTestId(ID)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void startReturnsTrueOnlyWhenQueuedRowWasClaimed() {
        when(analyses.claimQueued(ID, "gpt-test", "{}"))
                .thenReturn(1);
        assertTrue(service.start(ID, "gpt-test", "{}"));
    }

    @Test
    void startReturnsFalseWhenAnotherWorkerAlreadyClaimedIt() {
        // Repository mock returns 0 updated rows by default.
        assertFalse(service.start(ID, "gpt-test", "{}"));
        verify(analyses).claimQueued(ID, "gpt-test", "{}");
    }

    @Test
    void completeMissingAnalysisFails() {
        when(analyses.findByBloodTestId(ID)).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.complete(ID, List.of()));
        verifyNoInteractions(explanations);
    }

    @Test
    void cannotCompleteQueuedAnalysis() {
        BloodTestAnalysis a = new BloodTestAnalysis(ID);
        when(analyses.findByBloodTestId(ID)).thenReturn(Optional.of(a));
        assertThrows(IllegalStateException.class,
                () -> service.complete(ID, List.of()));
        assertEquals(BloodTestAnalysis.Status.QUEUED, a.getStatus());
        verifyNoInteractions(explanations);
    }

    @Test
    void completingEmptyResultMarksItCompleted() {
        BloodTestAnalysis a = processing();
        service.complete(ID, List.of());
        assertEquals(BloodTestAnalysis.Status.COMPLETED, a.getStatus());
        assertNotNull(a.getCompletedAt());
        verify(explanations).saveAll(List.of());
    }

    @Test
    void completingAiResultPersistsIndicatorExplanationWithJsonCauses() {
        BloodTestAnalysis a = processing();
        var ai = new OpenAiAnalysisClient.Explanation(
                99L, "Hemoglobino reikšmės paaiškinimas",
                List.of("Priežastis 1", "Priežastis 2"),
                "Aptarkite su gydytoju");

        service.complete(ID, List.of(ai));

        verify(explanations).saveAll(argThat(saved -> {
            var iterator = saved.iterator();
            if (!iterator.hasNext()) return false;
            IndicatorExplanation item = iterator.next();
            return !iterator.hasNext()
                    && item.getIndicatorId().equals(99L)
                    && item.getMeaning().equals("Hemoglobino reikšmės paaiškinimas")
                    && item.getPracticalAdvice().equals("Aptarkite su gydytoju")
                    && item.getPossibleCausesJson().contains("Priežastis 1")
                    && item.getPossibleCausesJson().contains("Priežastis 2");
        }));
        assertEquals(BloodTestAnalysis.Status.COMPLETED, a.getStatus());
    }

    @Test
    void failChangesOnlyProcessingAnalysis() {
        BloodTestAnalysis a = processing();
        service.fail(ID);
        assertEquals(BloodTestAnalysis.Status.FAILED, a.getStatus());
    }

    @Test
    void failLeavesCompletedAnalysisUnchanged() {
        BloodTestAnalysis a = new BloodTestAnalysis(ID);
        a.markProcessing("gpt-test", "{}");
        a.markCompleted();
        when(analyses.findByBloodTestId(ID)).thenReturn(Optional.of(a));
        service.fail(ID);
        assertEquals(BloodTestAnalysis.Status.COMPLETED, a.getStatus());
    }

    @Test
    void failDoesNothingWhenAnalysisMissing() {
        when(analyses.findByBloodTestId(ID)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.fail(ID));
    }

    @Test
    void failQueuedDelegatesToRepository() {
        service.failQueued(ID);
        verify(analyses).markQueuedAsFailed(ID);
    }
}
