package lt.reviv.bloodtests.ai.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reviv.analysis")
public record AnalysisProperties(
        boolean enabled,
        String model,
        int maxOutputTokens,
        int maxAttempts
) {
}
