package lt.reviv.push.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Clock for reminders: intake times (08:00, 13:00, 19:00) are interpreted in the configured time zone. */
@Configuration
public class ReminderClockConfig {

    @Bean
    public Clock reminderClock(ReminderProperties properties) {
        return Clock.system(ZoneId.of(properties.zone()));
    }
}