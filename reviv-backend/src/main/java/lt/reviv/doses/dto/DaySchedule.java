package lt.reviv.doses.dto;

import java.time.LocalDate;
import java.util.List;

/** The doses planned for one day. Doses are listed only for an ACTIVE plan. */
public record DaySchedule(LocalDate date, Long planId, String planStatus, List<ScheduledDose> doses) {
}