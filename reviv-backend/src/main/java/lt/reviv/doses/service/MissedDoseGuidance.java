package lt.reviv.doses.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Instruction after a dose was marked as missed (NI US-13, criterion 3): drink it now if more than half of the
 * interval until the next dose is left, otherwise skip it and do not take a double dose.
 */
public final class MissedDoseGuidance {

    public enum Action { DRINK_NOW, SKIP }

    public record Advice(Action action, String message) {
    }

    private static final Duration INTERVAL = Duration.ofHours(24);

    private MissedDoseGuidance() {
    }

    /** Each plan entry is taken once a day, so the next dose is due at the same time tomorrow (interval 24 h). */
    public static Advice advise(LocalDateTime now, LocalTime doseTime) {
        LocalDateTime nextDose = LocalDateTime.of(now.toLocalDate(), doseTime).plusDays(1);
        Duration remaining = Duration.between(now, nextDose);
        if (remaining.compareTo(INTERVAL.dividedBy(2)) > 0) {
            return new Advice(Action.DRINK_NOW,
                    "Iki kitos dozės liko daugiau nei pusė laiko tarpo, todėl praleistą dozę galite išgerti dabar. "
                            + "Rytojaus dozės nedvigubinkite.");
        }
        return new Advice(Action.SKIP,
                "Iki kitos dozės liko mažiau nei pusė laiko tarpo, todėl praleistą dozę praleiskite "
                        + "ir negerkite dvigubos dozės.");
    }
}