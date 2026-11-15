package lt.reviv.bloodtests.repository.plan;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConsumptionPlanRepository extends JpaRepository<ConsumptionPlan, Long> {
    List<ConsumptionPlan> findByBloodTestIdAndRecommendationGenerationIdOrderByIdDesc(Long bloodTestId, Long generationId);

    // Metadata only: the blood_test PDF bytes are deliberately not loaded for the plan list.
    @Query("""
        select new lt.reviv.bloodtests.repository.plan.PlanListRow(
            p.id, p.bloodTestId, p.status, p.createdAt, p.updatedAt,
            b.fileName, b.laboratory, b.testDate)
        from ConsumptionPlan p, BloodTest b, AppUser u
        where p.bloodTestId = b.id and b.userId = u.id and u.googleId = :googleId
        order by p.createdAt desc, p.id desc
        """)
    List<PlanListRow> findAllForUser(@Param("googleId") String googleId);

    @Query("""
        select p from ConsumptionPlan p, BloodTest b, AppUser u
        where p.id = :id and p.bloodTestId = b.id
          and b.userId = u.id and u.googleId = :googleId
        """)
    Optional<ConsumptionPlan> findOwnedById(@Param("id") Long id, @Param("googleId") String googleId);
}
