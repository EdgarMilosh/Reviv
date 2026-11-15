package lt.reviv.bloodtests.controller.plan;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Preserve safe, user-facing US-11 validation errors in the HTTP response body. */
@RestControllerAdvice(basePackages = "lt.reviv.bloodtests.controller.plan")
public class PlanErrorAdvice {
    private static final Logger log = LoggerFactory.getLogger(PlanErrorAdvice.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handlePlanDatabaseError(DataAccessException error) {
        log.error("US-11 database failure when saving plan", error);
        ProblemDetail details = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Nepavyko išsaugoti plano duomenų bazėje. Patikrinkite backend žurnalą.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(details);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handlePlanInternalError(IllegalStateException error) {
        log.error("US-11 internal plan generation failure", error);
        ProblemDetail details = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Plano generavimas nutrūko serveryje. Klaidos detalės yra backend žurnale.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(details);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handlePlanConflict(ResponseStatusException error) {
        HttpStatusCode status = error.getStatusCode();
        ProblemDetail details = ProblemDetail.forStatusAndDetail(status,
                error.getReason() == null ? "Nepavyko apdoroti plano." : error.getReason());
        return ResponseEntity.status(status).body(details);
    }
}
