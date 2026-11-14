package lt.reviv.bloodtests.repository;

import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BloodTestAnalysisRepository
        extends JpaRepository<BloodTestAnalysis, Long> {

    Optional<BloodTestAnalysis> findByBloodTestId(
            Long bloodTestId
    );

    boolean existsByBloodTestId(Long bloodTestId);


    @Modifying
    @Query(value = """
        INSERT INTO blood_test_analyses
            (blood_test_id, status, created_at)
        VALUES
            (:bloodTestId, 'QUEUED', CURRENT_TIMESTAMP)
        ON CONFLICT (blood_test_id) DO NOTHING
        """, nativeQuery = true)
    int enqueueIfAbsent(
            @Param("bloodTestId") Long bloodTestId
    );

    @Modifying
    @Query(value = """
        UPDATE blood_test_analyses
        SET status = 'PROCESSING',
            ai_model = :model,
            profile_snapshot = :profileSnapshot
        WHERE blood_test_id = :bloodTestId
          AND status = 'QUEUED'
        """, nativeQuery = true)
    int claimQueued(
            @Param("bloodTestId") Long bloodTestId,
            @Param("model") String model,
            @Param("profileSnapshot") String profileSnapshot
    );


    List<BloodTestAnalysis>
    findTop3ByStatusOrderByCreatedAtAsc(
            BloodTestAnalysis.Status status
    );

    @Modifying
    @Query(value = """
        UPDATE blood_test_analyses
        SET status = 'FAILED'
        WHERE blood_test_id = :bloodTestId
          AND status = 'QUEUED'
        """, nativeQuery = true)
    int markQueuedAsFailed(
            @Param("bloodTestId") Long bloodTestId
    );






}
