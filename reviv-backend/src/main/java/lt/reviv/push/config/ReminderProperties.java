package lt.reviv.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Reminder delivery settings (reviv.reminders.*). */
@ConfigurationProperties(prefix = "reviv.reminders")
public record ReminderProperties(Boolean enabled, String zone, Integer catchUpMinutes, Integer cycleNoticeHour) {

    public ReminderProperties {
        enabled = enabled == null || enabled;
        zone = zone == null || zone.isBlank() ? "Europe/Vilnius" : zone.trim();
        catchUpMinutes = catchUpMinutes == null || catchUpMinutes < 1 ? 10 : catchUpMinutes;
        cycleNoticeHour = cycleNoticeHour == null || cycleNoticeHour < 0 || cycleNoticeHour > 23 ? 9 : cycleNoticeHour;
    }
}