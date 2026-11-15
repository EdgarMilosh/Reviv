package lt.reviv.push.controller;

import jakarta.validation.Valid;
import lt.reviv.push.dto.PublicKeyResponse;
import lt.reviv.push.dto.PushSubscriptionRequest;
import lt.reviv.push.service.PushSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Web Push subscription API (US-12). Available to registered users. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushSubscriptionService service;

    public PushController(PushSubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/public-key")
    public PublicKeyResponse publicKey() {
        return new PublicKeyResponse(service.publicKey());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PushSubscriptionRequest request) {
        service.subscribe(jwt, request);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal Jwt jwt, @RequestParam String endpoint) {
        service.unsubscribe(jwt, endpoint);
    }
}