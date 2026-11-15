package lt.reviv.bloodtests.repository.plan;

import lt.reviv.bloodtests.domain.plan.PlanReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlanReminderRepository extends JpaRepository<PlanReminder, Long> {
    Optional<PlanReminder> findByPlanEntryId(Long entryId);
}
