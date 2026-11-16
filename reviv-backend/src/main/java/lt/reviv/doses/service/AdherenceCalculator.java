package lt.reviv.doses.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Counting of planned doses, adherence percentage and the "plan was not followed" rule (NI US-13, 4 and 6). */
public final class AdherenceCalculator {

    private AdherenceCalculator() {
    }

    /**
     * Number of daily doses planned for a cycle [start, end] inside the period [from, to], counting only days up to
     * and including {@code through} (the last day whose dose can already be judged).
     */
    public static long plannedDoses(LocalDate start, LocalDate end, LocalDate from, LocalDate to, LocalDate through) {
        LocalDate first = max(start, from);
        LocalDate last = min(end, min(to, through));
        if (last.isBefore(first)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(first, last) + 1;
    }

    /** Adherence in percent, or null when nothing was planned yet. */
    public static Integer percent(long taken, long planned) {
        if (planned <= 0) {
            return null;
        }
        return (int) Math.round(taken * 100.0 / planned);
    }

    /** True when more than {@code thresholdPercent} percent of the planned doses were not taken. */
    public static boolean notFollowed(long planned, long taken, int thresholdPercent) {
        return planned > 0 && (planned - taken) * 100 > (long) thresholdPercent * planned;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}