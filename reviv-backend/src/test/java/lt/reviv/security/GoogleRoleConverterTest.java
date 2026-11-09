package lt.reviv.security;

import lt.reviv.common.security.GoogleRoleConverter;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** US-1: rolė imama iš duomenų bazės pagal Google ID (sub), o ne iš pačio žetono. */
@ExtendWith(MockitoExtension.class)
class GoogleRoleConverterTest {

    @Mock
    private AppUserRepository repository;

    @InjectMocks
    private GoogleRoleConverter converter;

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("google-1")
                .build();
    }

    private static List<String> roles(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void registeredUserGetsRoleUser() {
        when(repository.findByGoogleId("google-1"))
                .thenReturn(Optional.of(new AppUser("google-1", "a@gmail.com", "A", UserRole.USER)));

        AbstractAuthenticationToken token = converter.convert(jwt());

        assertEquals(List.of("ROLE_USER"), roles(token));
        assertEquals("google-1", token.getName());
    }

    @Test
    void adminGetsRoleAdmin() {
        when(repository.findByGoogleId("google-1"))
                .thenReturn(Optional.of(new AppUser("google-1", "a@gmail.com", "A", UserRole.ADMIN)));

        assertEquals(List.of("ROLE_ADMIN"), roles(converter.convert(jwt())));
    }

    @Test
    void unregisteredUserGetsNoRoles() {   // TA-09
        when(repository.findByGoogleId("google-1")).thenReturn(Optional.empty());

        AbstractAuthenticationToken token = converter.convert(jwt());

        assertTrue(roles(token).isEmpty());
        assertTrue(token.isAuthenticated(), "Žetonas galioja, bet rolės nėra – leidžiama tik registracija");
    }
}