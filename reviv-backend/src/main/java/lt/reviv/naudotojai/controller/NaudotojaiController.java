package lt.reviv.naudotojai.controller;

import jakarta.validation.Valid;
import lt.reviv.naudotojai.dto.NaudotojoInfo;
import lt.reviv.naudotojai.dto.RegistracijaUzklausa;
import lt.reviv.naudotojai.service.NaudotojaiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** DP1 – Naudotojų kontrolerio komponentas (sąsaja /api/naudotojai). */
@RestController
@RequestMapping("/api/naudotojai")
public class NaudotojaiController {

    private final NaudotojaiService service;

    public NaudotojaiController(NaudotojaiService service) {
        this.service = service;
    }

    @GetMapping("/as")
    public NaudotojoInfo as(@AuthenticationPrincipal Jwt jwt) {
        return service.dabartinis(jwt);
    }

    @PostMapping("/registracija")
    public NaudotojoInfo registruoti(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody RegistracijaUzklausa uzklausa) {
        return service.registruoti(jwt);
    }
}