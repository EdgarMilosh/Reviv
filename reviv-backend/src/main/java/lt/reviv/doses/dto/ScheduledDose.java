package lt.reviv.doses.dto;

import lt.reviv.bloodtests.domain.plan.PlanEntry;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** One dose of the daily schedule. {@code canCancel} is true when the mark was made today and can still be undone. */
public record ScheduledDose(
        Long entryId,
        String supplementName,
        BigDecimal dose,
        String doseUnit,
        PlanEntry.DayPart dayPart,
        PlanEntry.MealRelation mealRelation,
        LocalTime time,
        DoseState state,
        OffsetDateTime markedAt,
        boolean canCancel
) {
}