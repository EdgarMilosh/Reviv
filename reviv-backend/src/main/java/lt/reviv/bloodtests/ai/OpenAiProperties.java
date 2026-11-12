package lt.reviv.bloodtests.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reviv.ai")
public record OpenAiProperties(
        boolean enabled,
        String apiKey,
        String model,
        int maxPdfPages,
        int maxPdfBytes,
        int maxAttempts,
        int maxOutputTokens
) {}
