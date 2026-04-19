package com.resumeforge.ai.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory sliding-window rate limiter for AI endpoints.
 *
 * Strategy: fixed window per (userId, endpoint) pair.
 *   - Window size: 60 seconds
 *   - Max requests per window: configurable (default 10)
 *
 * Thread-safe. Evicts stale windows every 5 minutes to prevent memory growth.
 *
 * Upgrade path: swap ConcurrentHashMap for Redis when horizontally scaling.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    /** Max AI requests per user per minute. */
    public static final int AI_MAX_REQUESTS_PER_MINUTE = 10;

    /** Window size in seconds. */
    private static final long WINDOW_SECONDS = 60L;

    /** Evict windows not touched in this many seconds. */
    private static final long EVICTION_THRESHOLD_SECONDS = 300L;

    /**
     * Key: "userId:endpoint"
     * Value: current window state
     */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private ScheduledExecutorService evictionScheduler;

    @PostConstruct
    public void startEvictionTask() {
        evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-eviction");
            t.setDaemon(true);
            return t;
        });
        evictionScheduler.scheduleAtFixedRate(this::evictStaleWindows, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void stopEvictionTask() {
        if (evictionScheduler != null) {
            evictionScheduler.shutdownNow();
        }
    }

    /**
     * Attempts to consume one request slot for the given user and endpoint.
     *
     * @param userId   the authenticated user's ID
     * @param endpoint a short identifier for the AI endpoint (e.g. "summary", "bullets")
     * @return true if the request is allowed; false if rate limit is exceeded
     */
    public boolean tryConsume(Long userId, String endpoint) {
        String key = userId + ":" + endpoint;
        long nowSeconds = Instant.now().getEpochSecond();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || nowSeconds >= existing.windowEnd) {
                // Start a fresh window
                return new Window(nowSeconds + WINDOW_SECONDS, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            existing.lastAccessedAt = nowSeconds;
            return existing;
        });

        boolean allowed = window.count.get() <= AI_MAX_REQUESTS_PER_MINUTE;

        if (!allowed) {
            log.warn("Rate limit exceeded: userId={} endpoint={} count={}",
                    userId, endpoint, window.count.get());
        }

        return allowed;
    }

    /**
     * Returns the number of seconds until the current window resets for this user/endpoint.
     * Used to populate the Retry-After header in 429 responses.
     */
    public long secondsUntilReset(Long userId, String endpoint) {
        String key = userId + ":" + endpoint;
        Window window = windows.get(key);
        if (window == null) return WINDOW_SECONDS;
        long remaining = window.windowEnd - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    private void evictStaleWindows() {
        long cutoff = Instant.now().getEpochSecond() - EVICTION_THRESHOLD_SECONDS;
        int evicted = 0;
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> entry = it.next();
            if (entry.getValue().lastAccessedAt < cutoff) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("Rate limit eviction: removed {} stale windows. Active windows: {}",
                    evicted, windows.size());
        }
    }

    private static final class Window {
        final long windowEnd;
        final AtomicInteger count;
        volatile long lastAccessedAt;

        Window(long windowEnd, AtomicInteger count) {
            this.windowEnd = windowEnd;
            this.count = count;
            this.lastAccessedAt = Instant.now().getEpochSecond();
        }
    }
}
