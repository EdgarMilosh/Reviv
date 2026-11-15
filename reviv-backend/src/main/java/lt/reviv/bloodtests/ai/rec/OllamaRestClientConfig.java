package lt.reviv.bloodtests.ai.rec;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaRestClientConfig {

    @Bean("ollamaRestClient")
    public RestClient ollamaRestClient(
            RestClient.Builder builder,
            OllamaProperties properties
    ) {
        var factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(180));

        return builder
                .requestFactory(factory)
                .baseUrl(properties.baseUrl())
                .build();
    }
}