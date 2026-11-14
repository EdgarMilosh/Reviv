package lt.reviv.supplements.controller;

import jakarta.validation.Valid;
import lt.reviv.supplements.dto.InteractionRequest;
import lt.reviv.supplements.dto.SupplementRequest;
import lt.reviv.supplements.dto.SupplementResponse;
import lt.reviv.supplements.service.SupplementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** DP3 – Papildų kontrolerio komponentas. Katalogo API leidžiamas tik turinio administratoriui (NFR-15). */
@RestController
@RequestMapping("/api/supplements")
@PreAuthorize("hasRole('ADMIN')")
public class SupplementController {

    private final SupplementService supplementService;

    public SupplementController(SupplementService supplementService) {
        this.supplementService = supplementService;
    }

    @GetMapping
    public List<SupplementResponse> list() {
        return supplementService.list();
    }

    @GetMapping("/{id}")
    public SupplementResponse get(@PathVariable Long id) {
        return supplementService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplementResponse create(@Valid @RequestBody SupplementRequest request) {
        return supplementService.create(request);
    }

    @PutMapping("/{id}")
    public SupplementResponse update(@PathVariable Long id, @Valid @RequestBody SupplementRequest request) {
        return supplementService.update(id, request);
    }

    /** 204 – papildas ištrintas; 200 su įrašu – papildas naudojamas planuose ir pažymėtas nebenaudojamu. */
    @DeleteMapping("/{id}")
    public ResponseEntity<SupplementResponse> delete(@PathVariable Long id) {
        return supplementService.delete(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplementResponse addInteraction(@PathVariable Long id, @Valid @RequestBody InteractionRequest request) {
        return supplementService.addInteraction(id, request);
    }

    @DeleteMapping("/{id}/interactions/{interactionId}")
    public SupplementResponse removeInteraction(@PathVariable Long id, @PathVariable Long interactionId) {
        return supplementService.removeInteraction(id, interactionId);
    }
}