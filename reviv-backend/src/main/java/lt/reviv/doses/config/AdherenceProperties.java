package lt.reviv.doses.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dose tracking settings (reviv.adherence.*). */
@ConfigurationProperties(prefix = "reviv.adherence")
public record AdherenceProperties(Integer missedThresholdPercent) {

    public AdherenceProperties {
        missedThresholdPercent = missedThresholdPercent == null
                || missedThresholdPercent < 1 || missedThresholdPercent > 99 ? 50 : missedThresholdPercent;
    }
}