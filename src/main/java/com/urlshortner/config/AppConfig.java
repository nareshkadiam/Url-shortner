package com.urlshortner.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public static final String CLICK_EXECUTOR = "clickExecutor";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Bounded executor for click counting. When saturated, clicks are dropped (and logged)
     * rather than slowing down redirects.
     */
    @Bean(CLICK_EXECUTOR)
    public ThreadPoolTaskExecutor clickExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("click-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Click executor saturated; dropping click increment"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
