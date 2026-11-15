package lt.reviv.bloodtests.repository.rec;

import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationGenerationRepository
        extends JpaRepository<RecommendationGeneration, Long> {

    List<RecommendationGeneration> findByBloodTestIdOrderByCreatedAtDescIdDesc(Long bloodTestId);
}
