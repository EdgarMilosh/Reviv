package lt.reviv.bloodtests.repository.plan;

import lt.reviv.bloodtests.domain.plan.PlanEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlanEntryRepository extends JpaRepository<PlanEntry, Long> {
    List<PlanEntry> findByPlanIdOrderByIdAsc(Long planId);
    List<PlanEntry> findByPlanIdInOrderByPlanIdAscIdAsc(Collection<Long> planIds);
    Optional<PlanEntry> findByIdAndPlanId(Long id, Long planId);
    boolean existsBySupplementId(Long supplementId);
}
