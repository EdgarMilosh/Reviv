package lt.reviv.supplements.repository;

import lt.reviv.supplements.domain.SupplementInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplementInteractionRepository extends JpaRepository<SupplementInteraction, Long> {

    List<SupplementInteraction> findBySupplementAIdOrSupplementBId(Long supplementAId, Long supplementBId);

    Optional<SupplementInteraction> findBySupplementAIdAndSupplementBId(Long supplementAId, Long supplementBId);
}