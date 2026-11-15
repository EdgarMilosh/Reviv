package lt.reviv.domain;

import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationGenerationTest {
    @Test
    void keepsModelBloodTestAndProfileSnapshot() {
        var generation = new RecommendationGeneration(1L, "qwen3:14b", "{\"age\":22}", true);
        assertNull(generation.getId());
        assertEquals(1L, generation.getBloodTestId());
        assertEquals("qwen3:14b", generation.getAiModel());
        assertEquals("{\"age\":22}", generation.getProfileSnapshotJson());
        assertTrue(generation.isCriticalityNotAssessed());
        assertNotNull(generation.getCreatedAt());
    }

    @Test
    void allowsGenerationWithAssessedCriticality() {
        var generation = new RecommendationGeneration(2L, "test-model", "{}", false);
        assertFalse(generation.isCriticalityNotAssessed());
    }
}
