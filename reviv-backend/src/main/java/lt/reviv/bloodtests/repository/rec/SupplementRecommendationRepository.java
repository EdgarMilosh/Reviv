package lt.reviv.bloodtests.repository.rec;

import jakarta.persistence.LockModeType;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface SupplementRecommendationRepository
        extends JpaRepository<SupplementRecommendation, Long> {

    List<SupplementRecommendation> findByGenerationIdOrderByIdAsc(Long generationId);

    // Uztikrina, kad du vienalaikiai paspaudimai nepakeis to paties PENDING iraso.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SupplementRecommendation> findByIdAndGenerationId(Long id, Long generationId);
}
