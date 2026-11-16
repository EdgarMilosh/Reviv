package lt.reviv.doses.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Dose history for a period. {@code planned} counts days whose dose can already be judged (yesterday and earlier,
 * plus today if it was marked); {@code notFollowed} is set once the cycle has ended and too many doses were missed.
 */
public record HistorySummary(
        Long planId,
        LocalDate from,
        LocalDate to,
        long planned,
        long taken,
        long skipped,
        long unmarked,
        Integer adherencePercent,
        boolean cycleEnded,
        boolean notFollowed,
        String notice,
        List<EntryHistory> entries
) {
}