package lt.reviv.service;

import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.push.service.CycleEntry;
import lt.reviv.push.service.DueReminder;
import lt.reviv.push.service.PushMessage;
import lt.reviv.push.service.ReminderMessages;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** US-12: reminder texts (supplement name, dose, relation to meals) and where a click leads. */
class ReminderMessagesTest {

    private static DueReminder reminder(String dose, PlanEntry.MealRelation meal) {
        return new DueReminder(5L, 6L, 7L, 8L, "Cinkas", new BigDecimal(dose), "mg", meal);
    }

    @Test
    void TA49_intakeMessageShowsSupplementDoseAndMealRelation() {
        PushMessage message = ReminderMessages.intake(reminder("15.0000", PlanEntry.MealRelation.AFTER_MEAL));

        assertEquals("Laikas išgerti Cinkas", message.title());
        assertEquals("15 mg, po valgio", message.body());
        assertEquals("/plans/7", message.url());
    }

    @Test
    void TA49_intakeMessageDescribesEveryMealRelation() {
        assertEquals("15 mg, prieš valgį",
                ReminderMessages.intake(reminder("15", PlanEntry.MealRelation.BEFORE_MEAL)).body());
        assertEquals("15 mg, valgant",
                ReminderMessages.intake(reminder("15", PlanEntry.MealRelation.WITH_MEAL)).body());
    }

    @Test
    void TA49_intakeMessageOmitsMealWhenItDoesNotMatter() {
        assertEquals("15 mg", ReminderMessages.intake(reminder("15", PlanEntry.MealRelation.INDEPENDENT)).body());
        assertEquals("15 mg", ReminderMessages.intake(reminder("15", PlanEntry.MealRelation.UNKNOWN)).body());
        assertEquals("15 mg", ReminderMessages.intake(reminder("15", null)).body());
    }

    @Test
    void TA49_doseIsShownWithoutTrailingZeros() {
        assertEquals("1000 mg", ReminderMessages.intake(reminder("1000.0000", null)).body());
        assertEquals("0.5 mg", ReminderMessages.intake(reminder("0.5000", null)).body());
        assertEquals("100 mg", ReminderMessages.intake(reminder("100", null)).body());
    }

    @Test
    void TA58_cycleEndingMessageNamesSupplementAndEndDate() {
        CycleEntry entry = new CycleEntry(1L, 7L, 8L, "Selenas", LocalDate.of(2026, 11, 19));

        PushMessage message = ReminderMessages.cycleEnding(entry);

        assertEquals("Netrukus baigsis ciklas", message.title());
        assertEquals("Selenas: ciklas baigiasi 2026-11-19. Pasiruoškite tyrimo kartojimui.", message.body());
        assertEquals("/plans/7", message.url());
    }

    @Test
    void TA58_cycleEndedMessageAsksToRepeatTheTest() {
        CycleEntry entry = new CycleEntry(1L, 7L, 8L, "Jodas", LocalDate.of(2026, 11, 15));

        PushMessage message = ReminderMessages.cycleEnded(entry);

        assertEquals("Ciklas baigėsi", message.title());
        assertEquals("Jodas: rekomenduojama pakartoti kraujo tyrimą.", message.body());
        assertEquals("/upload", message.url());
    }
}