package lt.reviv.controller;

import lt.reviv.users.controller.UserController;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.dto.RegistrationRequest;
import lt.reviv.users.dto.UserInfo;
import lt.reviv.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-1: DP1 - Naudotojų kontrolerio komponentas. */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService service;

    @InjectMocks
    private UserController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("google-1")
                .build();
    }

    private static UserInfo info() {
        return new UserInfo(1L, "a@gmail.com", "A", UserRole.USER);
    }

    @Test
    void meReturnsCurrentUserFromService() {
        Jwt jwt = jwt();
        UserInfo info = info();
        when(service.current(jwt)).thenReturn(info);

        assertSame(info, controller.me(jwt));
    }

    @Test
    void registerDelegatesToService() {
        Jwt jwt = jwt();
        UserInfo info = info();
        when(service.register(jwt)).thenReturn(info);

        assertSame(info, controller.register(jwt, new RegistrationRequest(true)));
        verify(service).register(jwt);
    }
}