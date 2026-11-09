package lt.reviv.service;

import lt.reviv.common.config.RevivProperties;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.dto.UserInfo;
import lt.reviv.users.repository.AppUserRepository;
import lt.reviv.users.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-1: registracija ir prisijungimas per Google (DP5 - Naudotojų valdymo komponentas). */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository repository;

    private UserService service;

    @BeforeEach
    void setUp() {
        RevivProperties properties = new RevivProperties(
                "client-id", List.of("Admin@Gmail.com"), List.of("http://localhost:5173"));
        service = new UserService(repository, properties);
    }

    private static Jwt jwt(String subject, String email, String name) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("name", name)
                .build();
    }

    @Test
    void TA01_currentThrowsWhenUserIsNotRegisteredYet() {
        when(repository.findByGoogleId("google-new")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.current(jwt("google-new", "naujas@gmail.com", "Naujas")));
    }

    @Test
    void TA02_registerCreatesUserWithUserRoleAndLowercaseEmail() {
        when(repository.findByGoogleId("google-1")).thenReturn(Optional.empty());
        when(repository.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));

        UserInfo info = service.register(jwt("google-1", "Jonas.Jonaitis@Gmail.com", "Jonas Jonaitis"));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertEquals("google-1", saved.getGoogleId());
        assertEquals("jonas.jonaitis@gmail.com", saved.getEmail());
        assertEquals("Jonas Jonaitis", saved.getName());
        assertEquals(UserRole.USER, saved.getRole());
        assertNotNull(saved.getConsentDate(), "Sutikimo data turi būti išsaugota (NFR-16)");
        assertEquals(UserRole.USER, info.role());
        assertEquals("jonas.jonaitis@gmail.com", info.email());
    }

    @Test
    void TA04_registerGivesAdminRoleToEmailFromAdminList() {
        when(repository.findByGoogleId("google-2")).thenReturn(Optional.empty());
        when(repository.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));

        UserInfo info = service.register(jwt("google-2", "ADMIN@gmail.com", "Admin"));

        assertEquals(UserRole.ADMIN, info.role());
    }

    @Test
    void TA05_registerTwiceDoesNotCreateSecondAccount() {
        AppUser existing = new AppUser("google-1", "jonas@gmail.com", "Jonas", UserRole.USER);
        when(repository.findByGoogleId("google-1")).thenReturn(Optional.of(existing));

        UserInfo info = service.register(jwt("google-1", "jonas@gmail.com", "Jonas"));

        verify(repository, never()).save(any(AppUser.class));
        assertEquals("jonas@gmail.com", info.email());
        assertEquals(UserRole.USER, info.role());
    }

    @Test
    void TA06_currentReturnsRegisteredUser() {
        AppUser existing = new AppUser("google-1", "jonas@gmail.com", "Jonas", UserRole.USER);
        when(repository.findByGoogleId("google-1")).thenReturn(Optional.of(existing));

        UserInfo info = service.current(jwt("google-1", "jonas@gmail.com", "Jonas"));

        assertEquals("jonas@gmail.com", info.email());
        assertEquals(UserRole.USER, info.role());
    }

    @Test
    void TA07_currentRefreshesEmailAndNameChangedInGoogleAccount() {
        AppUser existing = new AppUser("google-1", "senas@gmail.com", "Senas Vardas", UserRole.USER);
        when(repository.findByGoogleId("google-1")).thenReturn(Optional.of(existing));

        UserInfo info = service.current(jwt("google-1", "Naujas@Gmail.com", "Naujas Vardas"));

        assertEquals("naujas@gmail.com", existing.getEmail());
        assertEquals("Naujas Vardas", existing.getName());
        assertEquals("naujas@gmail.com", info.email());
        assertEquals("Naujas Vardas", info.name());
    }
}