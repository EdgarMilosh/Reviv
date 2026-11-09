package lt.reviv.profile.service;

import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.dto.ProfileRequest;
import lt.reviv.profile.dto.ProfileResponse;
import lt.reviv.profile.exception.ProfileValidationException;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private ProfileService service;

    private AppUser user;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        user = new AppUser("google-123", "user@example.com", "Test User", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 11L);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("google-123")
                .build();
    }

    @Test
    void getCurrentProfileReturnsSavedProfile() {
        Profile profile = new Profile(user);
        profile.update(180, LocalDate.of(1990, 2, 14), new BigDecimal("72.50"),
                new String[]{"Rūkymas"}, new String[]{"Pienas"});

        when(appUserRepository.findByGoogleId("google-123")).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(11L)).thenReturn(Optional.of(profile));

        ProfileResponse response = service.getCurrentProfile(jwt);

        assertEquals(180, response.heightCm());
        assertEquals(new BigDecimal("72.50"), response.weightKg());
        assertArrayEquals(new String[]{"Rūkymas"}, response.harmfulHabits());
        assertArrayEquals(new String[]{"Pienas"}, response.allergies());
    }

    @Test
    void updateCurrentProfileAcceptsValidFreeTextAllergies() {
        when(appUserRepository.findByGoogleId("google-123")).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(11L)).thenReturn(Optional.empty());

        ProfileRequest request = new ProfileRequest(
                175,
                LocalDate.of(1995, 5, 20),
                new BigDecimal("68.00"),
                new String[]{"Alkoholio vartojimas"},
                new String[]{"Žiedadulkės", "Pienas", "Arašidų riešutas"}
        );

        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = service.updateCurrentProfile(jwt, request);

        assertEquals(175, response.heightCm());
        assertEquals(new BigDecimal("68.00"), response.weightKg());
        assertArrayEquals(new String[]{"Alkoholio vartojimas"}, response.harmfulHabits());
        assertArrayEquals(new String[]{"Žiedadulkės", "Pienas", "Arašidų riešutas"}, response.allergies());
        verify(profileRepository).save(any(Profile.class));
    }

    @Test
    void updateCurrentProfileRejectsUnknownHarmfulHabit() {
        when(appUserRepository.findByGoogleId("google-123")).thenReturn(Optional.of(user));

        ProfileRequest request = new ProfileRequest(
                170,
                LocalDate.of(1998, 7, 7),
                new BigDecimal("71.50"),
                new String[]{"Nežinomas įprotis"},
                new String[]{"Pienas"}
        );

        ProfileValidationException ex = assertThrows(ProfileValidationException.class,
                () -> service.updateCurrentProfile(jwt, request));

        assertEquals("harmfulHabits", ex.getField());
        assertTrue(ex.getMessage().contains("Invalid value"));
    }

    @Test
    void updateCurrentProfileRejectsInvalidFreeTextAllergy() {
        when(appUserRepository.findByGoogleId("google-123")).thenReturn(Optional.of(user));

        ProfileRequest request = new ProfileRequest(
                170,
                LocalDate.of(1998, 7, 7),
                new BigDecimal("71.50"),
                new String[]{"Rūkymas"},
                new String[]{"!!!"}
        );

        ProfileValidationException ex = assertThrows(ProfileValidationException.class,
                () -> service.updateCurrentProfile(jwt, request));

        assertEquals("allergies", ex.getField());
    }
}
