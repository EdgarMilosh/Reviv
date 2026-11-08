package lt.reviv.naudotojai.repository;

import lt.reviv.naudotojai.domain.Naudotojas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** DVP1 – Naudotojų duomenų komponentas. */
public interface NaudotojasRepository extends JpaRepository<Naudotojas, Long> {
    Optional<Naudotojas> findByGoogleId(String googleId);
}