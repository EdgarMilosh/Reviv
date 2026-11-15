package lt.reviv.push.repository;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.push.domain.PushDelivery;
import lt.reviv.push.service.CycleEntry;
import lt.reviv.push.service.DueReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {

    /** Daily reminders of ACTIVE plans whose time is within [since, until] and that were not sent today. */
    @Query("""
            select new lt.reviv.push.service.DueReminder(
                r.id, e.id, p.id, t.userId, e.supplementNameSnapshot, e.dailyDose, e.doseUnit, e.mealRelation)
            from PlanReminder r
            join PlanEntry e on e.id = r.planEntryId
            join ConsumptionPlan p on p.id = e.planId
            join BloodTest t on t.id = p.bloodTestId
            where r.enabled = true
              and p.status = :active
              and r.reminderTime >= :since and r.reminderTime <= :until
              and e.startDate <= :today and e.endDate >= :today
              and not exists (
                  select 1 from PushDelivery d
                  where d.kind = :kind and d.subjectId = r.id and d.deliveryDate = :today)
            """)
    List<DueReminder> findDueIntake(@Param("since") LocalTime since,
                                    @Param("until") LocalTime until,
                                    @Param("today") LocalDate today,
                                    @Param("active") ConsumptionPlan.Status active,
                                    @Param("kind") PushDelivery.Kind kind);

    /** Entries of ACTIVE plans whose cycle ends on the given day and were not notified today. */
    @Query("""
            select new lt.reviv.push.service.CycleEntry(e.id, p.id, t.userId, e.supplementNameSnapshot, e.endDate)
            from PlanEntry e
            join ConsumptionPlan p on p.id = e.planId
            join BloodTest t on t.id = p.bloodTestId
            where p.status = :active
              and e.endDate = :endDate
              and not exists (
                  select 1 from PushDelivery d
                  where d.kind = :kind and d.subjectId = e.id and d.deliveryDate = :today)
            """)
    List<CycleEntry> findCycleEntries(@Param("endDate") LocalDate endDate,
                                      @Param("today") LocalDate today,
                                      @Param("active") ConsumptionPlan.Status active,
                                      @Param("kind") PushDelivery.Kind kind);
}