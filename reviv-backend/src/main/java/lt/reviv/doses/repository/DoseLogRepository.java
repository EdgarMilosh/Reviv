package lt.reviv.doses.repository;

import lt.reviv.doses.domain.DoseLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** DVP4 - plan data component: dose marks. */
public interface DoseLogRepository extends JpaRepository<DoseLog, Long> {

    Optional<DoseLog> findByPlanEntryIdAndDoseDate(Long planEntryId, LocalDate doseDate);

    List<DoseLog> findByPlanEntryIdInAndDoseDateBetween(Collection<Long> planEntryIds, LocalDate from, LocalDate to);

    long deleteByPlanEntryIdAndDoseDate(Long planEntryId, LocalDate doseDate);
}