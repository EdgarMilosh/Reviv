package lt.reviv.bloodtests.repository;

import lt.reviv.bloodtests.domain.ExtractionIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtractionIssueRepository
        extends JpaRepository<ExtractionIssue, Long> {

    List<ExtractionIssue> findByBloodTestIdOrderByCreatedAtAsc(
            Long bloodTestId
    );

    List<ExtractionIssue> findByBloodTestIdAndResolvedFalse(
            Long bloodTestId
    );

    boolean existsByBloodTestIdAndResolvedFalse(
            Long bloodTestId
    );

    Optional<ExtractionIssue> findByIdAndBloodTestId(
            Long id,
            Long bloodTestId
    );
}
