package lt.reviv.doses.controller;

import jakarta.validation.Valid;
import lt.reviv.doses.dto.DaySchedule;
import lt.reviv.doses.dto.HistorySummary;
import lt.reviv.doses.dto.MarkRequest;
import lt.reviv.doses.dto.MarkResponse;
import lt.reviv.doses.service.DoseLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** DP4 - plan controller component: daily schedule, dose marks and history (US-13). */
@RestController
@RequestMapping("/api/planai/{planId}")
public class DoseLogController {

    private final DoseLogService service;

    public DoseLogController(DoseLogService service) {
        this.service = service;
    }

    @GetMapping("/schedule")
    public DaySchedule schedule(@PathVariable Long planId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @AuthenticationPrincipal Jwt jwt) {
        return service.schedule(planId, date, jwt);
    }

    @PutMapping("/entries/{entryId}/doses/{date}")
    public MarkResponse mark(@PathVariable Long planId,
                             @PathVariable Long entryId,
                             @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @Valid @RequestBody MarkRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return service.mark(planId, entryId, date, request.status(), jwt);
    }

    @DeleteMapping("/entries/{entryId}/doses/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long planId,
                       @PathVariable Long entryId,
                       @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @AuthenticationPrincipal Jwt jwt) {
        service.cancel(planId, entryId, date, jwt);
    }

    @GetMapping("/history")
    public HistorySummary history(@PathVariable Long planId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.history(planId, from, to, jwt);
    }
}