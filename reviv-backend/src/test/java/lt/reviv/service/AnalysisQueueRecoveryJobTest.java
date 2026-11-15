package lt.reviv.service;

import lt.reviv.bloodtests.ai.analysis.AnalysisProperties;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.service.analysis.AnalysisPersistenceService;
import lt.reviv.bloodtests.service.analysis.AnalysisQueueRecoveryJob;
import lt.reviv.bloodtests.service.analysis.BloodTestAnalysisExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisQueueRecoveryJobTest {
    @Mock BloodTestAnalysisRepository repository;
    @Mock BloodTestAnalysisExecutionService execution;
    @Mock AnalysisPersistenceService persistence;

    private AnalysisQueueRecoveryJob job(boolean enabled) {
        return new AnalysisQueueRecoveryJob(repository, execution, persistence,
                new AnalysisProperties(enabled, "gpt-test", 500, 2));
    }

    private BloodTestAnalysis analysis(long id) {
        return new BloodTestAnalysis(id);
    }

    @Test
    void disabledRecoveryDoesNotQueryDatabase() {
        job(false).recoverQueuedAnalyses();
        verifyNoInteractions(repository, execution, persistence);
    }

    @Test
    void emptyQueueDoesNotExecuteAnything() {
        when(repository.findTop3ByStatusOrderByCreatedAtAsc(
                BloodTestAnalysis.Status.QUEUED)).thenReturn(List.of());
        job(true).recoverQueuedAnalyses();
        verifyNoInteractions(execution, persistence);
    }

    @Test
    void processesMultipleQueuedAnalyses() {
        when(repository.findTop3ByStatusOrderByCreatedAtAsc(
                BloodTestAnalysis.Status.QUEUED))
                .thenReturn(List.of(analysis(5L), analysis(6L)));
        when(execution.execute(5L)).thenReturn(true);
        when(execution.execute(6L)).thenReturn(false);

        assertDoesNotThrow(() -> job(true).recoverQueuedAnalyses());
        verify(execution).execute(5L);
        verify(execution).execute(6L);
        verifyNoInteractions(persistence);
    }

    @Test
    void executionFailureMarksQueuedAnalysisAsFailed() {
        when(repository.findTop3ByStatusOrderByCreatedAtAsc(
                BloodTestAnalysis.Status.QUEUED))
                .thenReturn(List.of(analysis(5L)));
        when(execution.execute(5L)).thenThrow(new IllegalStateException("AI failure"));

        assertDoesNotThrow(() -> job(true).recoverQueuedAnalyses());
        verify(persistence).failQueued(5L);
    }

    @Test
    void failureToPersistFailureDoesNotStopNextAnalysis() {
        when(repository.findTop3ByStatusOrderByCreatedAtAsc(
                BloodTestAnalysis.Status.QUEUED))
                .thenReturn(List.of(analysis(5L), analysis(6L)));
        when(execution.execute(5L)).thenThrow(new IllegalStateException("AI failure"));
        when(execution.execute(6L)).thenReturn(true);
        doThrow(new IllegalStateException("DB error"))
                .when(persistence).failQueued(5L);

        assertDoesNotThrow(() -> job(true).recoverQueuedAnalyses());
        verify(persistence).failQueued(5L);
        verify(execution).execute(6L);
    }
}
