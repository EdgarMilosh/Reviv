package lt.reviv.users.repository;

import lt.reviv.users.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** DVP1 - User data component. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByGoogleId(String googleId);
}