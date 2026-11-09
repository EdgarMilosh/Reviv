package lt.reviv.profile.controller;

import jakarta.validation.Valid;
import lt.reviv.profile.dto.ProfileRequest;
import lt.reviv.profile.dto.ProfileResponse;
import lt.reviv.profile.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/", "/api"})
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getCurrentProfile(jwt);
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ProfileRequest request) {
        return profileService.updateCurrentProfile(jwt, request);
    }
}
