package com.resumeforge.ai.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks failed OTP verification attempts per email address.
 *
 * After MAX_FAILURES failed attempts within WINDOW_MINUTES minutes,
 * all further attempts for that email are rejected until the window expires.
 *
 * This prevents brute-force attacks against 6-digit OTPs.
 * (6-digit OTP = 1,000,000 combinations × 10 attempts/min = 100,000 minutes worst case,
 *  but without rate limiting an attacker could try all combinations in seconds.)
 *
 * Upgrade path: replace ConcurrentHashMap with Redis for multi-instance deployments.
 */
@Service
public class OtpAttemptService {

    private static final Logger log = LoggerFactory.getLogger(OtpAttemptService.class);

    public static final int MAX_FAILURES     = 5;
    public static final int WINDOW_MINUTES   = 15;
    private static final int EVICT_MINUTES   = 60;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private ScheduledExecutorService evictionScheduler;

    @PostConstruct
    public void startEviction() {
        evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "otp-attempt-eviction");
            t.setDaemon(true);
            return t;
        });
        evictionScheduler.scheduleAtFixedRate(this::evict, EVICT_MINUTES, EVICT_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void stopEviction() {
        if (evictionScheduler != null) evictionScheduler.shutdownNow();
    }

    /**
     * Returns true if the email is currently locked out.
     * Call BEFORE processing an OTP attempt.
     */
    public boolean isLockedOut(String email) {
        Attempt a = attempts.get(normalise(email));
        if (a == null) return false;
        if (Instant.now().isAfter(a.windowEnd)) {
            attempts.remove(normalise(email));
            return false;
        }
        return a.failures.get() >= MAX_FAILURES;
    }

    /**
     * Records a failed OTP attempt.
     * Call when the OTP is wrong or expired.
     */
    public void recordFailure(String email) {
        String key = normalise(email);
        Attempt a = attempts.compute(key, (k, existing) -> {
            if (existing == null || Instant.now().isAfter(existing.windowEnd)) {
                return new Attempt(Instant.now().plusSeconds(WINDOW_MINUTES * 60L));
            }
            existing.failures.incrementAndGet();
            return existing;
        });
        if (a.failures.get() >= MAX_FAILURES) {
            log.warn("OTP lockout triggered for email={}", key);
        }
    }

    /**
     * Clears the failure counter on successful OTP verification.
     */
    public void clearAttempts(String email) {
        attempts.remove(normalise(email));
    }

    /**
     * Returns seconds remaining in the lockout window, or 0 if not locked out.
     * Used to populate the error message shown to the user.
     */
    public long lockoutSecondsRemaining(String email) {
        Attempt a = attempts.get(normalise(email));
        if (a == null || a.failures.get() < MAX_FAILURES) return 0;
        long remaining = a.windowEnd.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    private void evict() {
        int removed = 0;
        Instant now = Instant.now();
        for (var it = attempts.entrySet().iterator(); it.hasNext(); ) {
            if (now.isAfter(it.next().getValue().windowEnd)) { it.remove(); removed++; }
        }
        if (removed > 0) log.debug("OTP attempt eviction: removed {} stale entries", removed);
    }

    private String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static final class Attempt {
        final Instant windowEnd;
        final AtomicInteger failures = new AtomicInteger(1);
        Attempt(Instant windowEnd) { this.windowEnd = windowEnd; }
    }
}
