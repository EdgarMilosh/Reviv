package lt.reviv.bloodtests.repository;

import lt.reviv.bloodtests.domain.BloodTestIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BloodTestIndicatorRepository
        extends JpaRepository<BloodTestIndicator, Long> {

    List<BloodTestIndicator> findByBloodTestIdOrderByIdAsc(
            Long bloodTestId
    );

    Optional<BloodTestIndicator> findByIdAndBloodTestId(
            Long id,
            Long bloodTestId
    );

    void deleteByBloodTestId(Long bloodTestId);
}
