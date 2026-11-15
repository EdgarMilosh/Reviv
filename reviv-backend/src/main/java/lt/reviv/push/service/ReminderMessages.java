package lt.reviv.push.service;

import lt.reviv.bloodtests.domain.plan.PlanEntry;

import java.math.BigDecimal;

/** Reminder texts: supplement name, dose and relation to meals (NI US-12, criterion 2). */
public final class ReminderMessages {

    private ReminderMessages() {
    }

    public static PushMessage intake(DueReminder reminder) {
        String dose = plain(reminder.dailyDose()) + " " + reminder.doseUnit();
        String meal = mealPhrase(reminder.mealRelation());
        String body = meal == null ? dose : dose + ", " + meal;
        return new PushMessage("Laikas išgerti " + reminder.supplementName(), body, "/plans/" + reminder.planId());
    }

    public static PushMessage cycleEnding(CycleEntry entry) {
        return new PushMessage("Netrukus baigsis ciklas",
                entry.supplementName() + ": ciklas baigiasi " + entry.endDate() + ". Pasiruoškite tyrimo kartojimui.",
                "/plans/" + entry.planId());
    }

    public static PushMessage cycleEnded(CycleEntry entry) {
        return new PushMessage("Ciklas baigėsi",
                entry.supplementName() + ": rekomenduojama pakartoti kraujo tyrimą.",
                "/upload");
    }

    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String mealPhrase(PlanEntry.MealRelation relation) {
        if (relation == null) {
            return null;
        }
        return switch (relation) {
            case BEFORE_MEAL -> "prieš valgį";
            case WITH_MEAL -> "valgant";
            case AFTER_MEAL -> "po valgio";
            default -> null;
        };
    }
}