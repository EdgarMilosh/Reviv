package lt.reviv.doses.dto;

/** Result of marking a dose; {@code guidance} is present only for a missed dose. */
public record MarkResponse(ScheduledDose dose, GuidanceView guidance) {
}