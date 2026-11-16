package lt.reviv.doses.dto;

import jakarta.validation.constraints.NotNull;
import lt.reviv.doses.domain.DoseLog;

public record MarkRequest(
        @NotNull(message = "Pasirinkite dozės būseną")
        DoseLog.Status status
) {
}