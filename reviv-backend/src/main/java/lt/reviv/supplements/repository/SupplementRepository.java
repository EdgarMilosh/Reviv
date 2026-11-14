package lt.reviv.supplements.repository;

import lt.reviv.supplements.domain.Supplement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** DVP3 – Papildų duomenų komponentas. */
public interface SupplementRepository extends JpaRepository<Supplement, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @EntityGraph(attributePaths = "distributors")
    List<Supplement> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "distributors")
    Optional<Supplement> findWithDistributorsById(Long id);
}