package lt.reviv.bloodtests.repository;

import lt.reviv.bloodtests.domain.IndicatorExplanation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IndicatorExplanationRepository
        extends JpaRepository<IndicatorExplanation, Long> {

    List<IndicatorExplanation> findByAnalysisIdOrderByIdAsc(
            Long analysisId
    );

    Optional<IndicatorExplanation> findByAnalysisIdAndIndicatorId(
            Long analysisId,
            Long indicatorId
    );
}
