package lt.reviv.profile.service;

import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.dto.ProfileRequest;
import lt.reviv.profile.dto.ProfileResponse;
import lt.reviv.profile.exception.ProfileValidationException;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class ProfileService {

    private static final List<String> HARMFUL_HABITS = List.of(
            "Rūkymas",
            "Alkoholio vartojimas",
            "Kofeinas",
            "Stresas",
            "Sėdimas gyvenimo būdas",
            "Per didelis ekrano naudojimas"
    );

    private final ProfileRepository profileRepository;
    private final AppUserRepository appUserRepository;

    public ProfileService(ProfileRepository profileRepository, AppUserRepository appUserRepository) {
        this.profileRepository = profileRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(Jwt jwt) {
        AppUser user = currentUser(jwt);
        return profileRepository.findByUserId(user.getId())
                .map(ProfileResponse::from)
                .orElseGet(() -> ProfileResponse.from(new Profile(user)));
    }

    @Transactional
    public ProfileResponse updateCurrentProfile(Jwt jwt, ProfileRequest request) {
        AppUser user = currentUser(jwt);
        Profile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> new Profile(user));

        validateInput(request);
        profile.update(
                request.heightCm(),
                request.birthDate(),
                request.weightKg(),
                sanitize(request.harmfulHabits()),
                sanitize(request.allergies())
        );

        return ProfileResponse.from(profileRepository.save(profile));
    }

    private AppUser currentUser(Jwt jwt) {
        return appUserRepository.findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new);
    }

    private void validateInput(ProfileRequest request) {
        if (request == null) {
            throw new ProfileValidationException("profile", "Profile data is required");
        }

        if (request.harmfulHabits() != null) {
            validateKnownHabits(request.harmfulHabits());
        }

        if (request.allergies() != null) {
            validateFreeTextValues(request.allergies(), "allergies");
        }
    }

    private void validateKnownHabits(String[] values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            String trimmed = value.trim();
            boolean allowed = HARMFUL_HABITS.stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .anyMatch(item -> item.equals(trimmed.toLowerCase()));

            if (!allowed) {
                throw new ProfileValidationException("harmfulHabits", "Invalid value: " + value);
            }
        }
    }

    private void validateFreeTextValues(String[] values, String field) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            String trimmed = value.trim();
            if (!isFreeTextEntry(trimmed)) {
                throw new ProfileValidationException(field, "Invalid value: " + value);
            }
        }
    }

    private boolean isFreeTextEntry(String value) {
        return value.length() >= 2 && value.chars().allMatch(ch ->
                Character.isLetterOrDigit(ch) || Character.isWhitespace(ch) || ch == '-' || ch == '_' || ch == '/' || ch == ',');
    }

    private String[] sanitize(String[] values) {
        if (values == null) {
            return new String[0];
        }

        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }
}
