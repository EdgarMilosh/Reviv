package lt.reviv.push.service;

import lt.reviv.bloodtests.domain.plan.PlanEntry;

import java.math.BigDecimal;

/** A daily intake reminder that is due now (from the US-11 plan). */
public record DueReminder(
        Long reminderId,
        Long entryId,
        Long planId,
        Long userId,
        String supplementName,
        BigDecimal dailyDose,
        String doseUnit,
        PlanEntry.MealRelation mealRelation
) {
}