package lt.reviv.bloodtests.domain.plan;

import java.time.LocalTime;
import java.util.List;

/** Fixed one-hour adjustment around each supplement schedule slot. */
public final class PlanScheduleTimes {
    private PlanScheduleTimes() {}

    public static LocalTime defaultFor(PlanEntry.DayPart dayPart) {
        return switch (dayPart) {
            case MORNING -> LocalTime.of(8, 0);
            case NOON -> LocalTime.of(13, 0);
            case EVENING -> LocalTime.of(19, 0);
        };
    }

    public static List<LocalTime> allowedFor(PlanEntry.DayPart dayPart) {
        LocalTime standard = defaultFor(dayPart);
        return List.of(standard.minusHours(1), standard, standard.plusHours(1));
    }

    public static boolean isAllowed(PlanEntry.DayPart dayPart, LocalTime time) {
        return dayPart != null && time != null && allowedFor(dayPart).contains(time);
    }
}
