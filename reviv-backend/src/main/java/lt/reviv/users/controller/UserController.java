package lt.reviv.users.controller;

import jakarta.validation.Valid;
import lt.reviv.users.dto.RegistrationRequest;
import lt.reviv.users.dto.UserInfo;
import lt.reviv.users.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** DP1 - User controller component (interface /api/users). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return service.current(jwt);
    }

    @PostMapping("/register")
    public UserInfo register(@AuthenticationPrincipal Jwt jwt,
                             @Valid @RequestBody RegistrationRequest request) {
        return service.register(jwt);
    }
}