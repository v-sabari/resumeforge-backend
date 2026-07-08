package com.resumeforge.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async thread pool for AI calls + Spring Retry enablement.
 *
 * AI-01 FIX: Added {@code @EnableRetry} so the RetryTemplate constructed
 * programmatically in {@link com.resumeforge.ai.service.AiService#init()}
 * operates within a properly configured Spring Retry context.
 *
 * Without {@code @EnableRetry} the RetryTemplate still works (it is a
 * plain Java object) but Spring's retry infrastructure beans (interceptors,
 * recovery callbacks) are not registered — which would break any future
 * use of {@code @Retryable} annotations on public methods.
 */
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig {

    /**
     * Dedicated thread pool for async OpenRouter HTTP calls.
     *
     * Sized for production on Render free tier (512 MB RAM):
     *   corePoolSize  10  — always-warm threads for concurrent AI requests
     *   maxPoolSize   50  — burst capacity (bounded to prevent OOM)
     *   queueCapacity 100 — buffer before tasks are rejected
     *
     * Each AI call holds a thread for up to readTimeout (60 s).
     * At maxPoolSize=50: supports up to 50 concurrent AI calls.
     * At Render free tier, keep maxPoolSize lower (20) if memory is tight.
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}