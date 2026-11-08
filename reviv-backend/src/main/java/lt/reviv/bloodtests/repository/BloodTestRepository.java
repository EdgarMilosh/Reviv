package lt.reviv.bloodtests.repository;

import lt.reviv.bloodtests.domain.BloodTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BloodTestRepository extends JpaRepository<BloodTest, Long> {

    boolean existsByUserIdAndFileSha256(
            Long userId,
            String fileSha256
    );

    List<BloodTest> findAllByUserIdOrderByUploadedAtDesc(
            Long userId
    );
}
