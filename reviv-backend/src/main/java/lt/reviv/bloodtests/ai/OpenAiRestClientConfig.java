package lt.reviv.bloodtests.ai;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiRestClientConfig {

    @Bean("openAiRestClient")
    public RestClient openAiRestClient(
            RestClient.Builder builder
    ) {
        var factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));

        return builder
                .requestFactory(factory)
                .baseUrl("https://api.openai.com")
                .build();
    }
}
