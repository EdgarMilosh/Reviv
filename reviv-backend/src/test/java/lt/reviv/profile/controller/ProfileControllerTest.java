package lt.reviv.profile.controller;

import lt.reviv.profile.dto.ProfileRequest;
import lt.reviv.profile.dto.ProfileResponse;
import lt.reviv.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService service;

    @InjectMocks
    private ProfileController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("google-123")
                .build();
    }

    @Test
    void getProfileReturnsCurrentProfile() {
        Jwt token = jwt();
        ProfileResponse expected = new ProfileResponse(
                175,
                LocalDate.of(1998, 4, 8),
                new BigDecimal("69.50"),
                new String[]{"Rūkymas"},
                new String[]{"Pienas"},
                OffsetDateTime.now()
        );

        when(service.getCurrentProfile(token)).thenReturn(expected);

        ProfileResponse actual = controller.getProfile(token);

        assertEquals(expected, actual);
        verify(service).getCurrentProfile(token);
    }

    @Test
    void updateProfileDelegatesToService() {
        Jwt token = jwt();
        ProfileRequest request = new ProfileRequest(
                180,
                LocalDate.of(1997, 2, 3),
                new BigDecimal("74.00"),
                new String[]{"Alkoholis"},
                new String[]{"Žiedadulkės"}
        );
        ProfileResponse expected = new ProfileResponse(
                180,
                LocalDate.of(1997, 2, 3),
                new BigDecimal("74.00"),
                new String[]{"Alkoholis"},
                new String[]{"Žiedadulkės"},
                OffsetDateTime.now()
        );

        when(service.updateCurrentProfile(token, request)).thenReturn(expected);

        ProfileResponse actual = controller.updateProfile(token, request);

        assertEquals(expected, actual);
        verify(service).updateCurrentProfile(token, request);
    }
}
