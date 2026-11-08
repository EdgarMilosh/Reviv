package lt.reviv.users.service;

import lt.reviv.common.config.RevivProperties;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.dto.UserInfo;
import lt.reviv.users.repository.AppUserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** DP5 - User management component (US-1, FR-01, FR-02). */
@Service
public class UserService {

    private final AppUserRepository repository;
    private final RevivProperties properties;

    public UserService(AppUserRepository repository, RevivProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Returns the signed-in user; if not registered yet, throws UserNotFoundException (404). */
    @Transactional
    public UserInfo current(Jwt jwt) {
        AppUser user = repository.findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new);
        user.updateDetails(emailOf(jwt), jwt.getClaimAsString("name"));
        return UserInfo.from(user);
    }

    /** Creates the account only after consent (NFR-16). Calling again returns the existing account. */
    @Transactional
    public UserInfo register(Jwt jwt) {
        return repository.findByGoogleId(jwt.getSubject())
                .map(UserInfo::from)
                .orElseGet(() -> {
                    String email = emailOf(jwt);
                    UserRole role = properties.adminEmails().contains(email)
                            ? UserRole.ADMIN : UserRole.USER;
                    AppUser created = new AppUser(jwt.getSubject(), email, jwt.getClaimAsString("name"), role);
                    return UserInfo.from(repository.save(created));
                });
    }

    private static String emailOf(Jwt jwt) {
        return jwt.getClaimAsString("email").toLowerCase(Locale.ROOT);
    }
}