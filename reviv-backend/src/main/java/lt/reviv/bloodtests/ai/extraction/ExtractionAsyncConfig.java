package lt.reviv.bloodtests.ai.extraction;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExtractionAsyncConfig {

    @Bean("pdfExtractionExecutor")
    public ThreadPoolTaskExecutor pdfExtractionExecutor() {
        var task = new ThreadPoolTaskExecutor();

        task.setCorePoolSize(1);
        task.setMaxPoolSize(1);
        task.setQueueCapacity(5);
        task.setThreadNamePrefix("reviv-pdf-");

        task.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        task.initialize();
        return task;
    }
}
