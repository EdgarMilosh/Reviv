package lt.reviv.bloodtests.ai.plan;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reviv.plan")
public record PlanProperties(boolean enabled, String model, int maxOutputTokens) {}
