package com.resumeforge.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * B10 FIX: dedicated thread pool for async AI calls.
 *
 * Previously AiService used RestTemplate synchronously, holding one Tomcat
 * thread blocked for the entire duration of each OpenRouter HTTP call
 * (typically 3–15 seconds). Under load this causes thread starvation.
 *
 * This executor offloads the blocking call to its own pool, freeing Tomcat
 * threads to accept new requests while AI calls are in-flight.
 *
 * Pool sizing:
 *   corePoolSize  10  — always-warm threads for typical concurrent AI load
 *   maxPoolSize   50  — burst capacity (bounded to prevent OOM)
 *   queueCapacity 100 — requests queue here when all 50 threads are busy
 *                       before being rejected with a clear error
 */
@Configuration
@EnableAsync
public class AsyncConfig {

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