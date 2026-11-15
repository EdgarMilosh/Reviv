package lt.reviv.bloodtests.ai.rec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reviv.recommendations.ollama")
public record OllamaProperties(
        boolean enabled,
        String baseUrl,
        String model
) {
}