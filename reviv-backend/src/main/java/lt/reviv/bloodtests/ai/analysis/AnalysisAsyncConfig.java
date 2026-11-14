package lt.reviv.bloodtests.ai.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AnalysisAsyncConfig {

    @Bean("analysisExecutor")
    public ThreadPoolTaskExecutor analysisExecutor() {

        var executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);

        executor.setThreadNamePrefix("reviv-analysis-");

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        executor.initialize();

        return executor;
    }
}
