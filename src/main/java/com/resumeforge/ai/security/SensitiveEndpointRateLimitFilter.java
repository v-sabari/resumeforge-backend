package com.resumeforge.ai.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEC FIX: per-IP sliding-window rate limiting for endpoints that are cheap to
 * spam and abuse-prone:
 *   - verify-email-otp : 10 attempts / 15 min (OTP brute force)
 *   - resend-email-otp : 5  attempts / 15 min (email bombing)
 *   - forgot-password  : 5  attempts / 15 min (reset-link email bombing)
 *   - contact          : 5  attempts / 15 min (spam)
 *
 * Unlike LoginRateLimitFilter (which counts only failures), these endpoints are
 * rate-limited on every POST — the attack is the volume of requests itself, so
 * there is no "success" that should reset the counter.
 *
 * The map is bounded to MAX_ENTRIES and pruned each request to avoid OOM.
 */
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveEndpointRateLimitFilter.class);

    private record LimitRule(String path, int maxAttempts) {}

    private static final List<LimitRule> RULES = List.of(
            new LimitRule("/api/auth/verify-email-otp", 10),
            new LimitRule("/api/auth/resend-email-otp", 5),
            new LimitRule("/api/auth/forgot-password", 5),
            new LimitRule("/api/contact", 5)
    );

    private static final long WINDOW_SECONDS = 15 * 60L;   // 15 minutes
    private static final int  MAX_ENTRIES    = 10_000;      // cap memory usage

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Per (path|IP): [attemptCount, windowStartEpochSeconds] */
    private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getServletPath();
        for (LimitRule rule : RULES) {
            if (rule.path().equals(path)) return false;
        }
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String path = request.getServletPath();
        int maxAttempts = maxAttemptsFor(path);

        String ip   = resolveClientIp(request);
        long   now  = Instant.now().getEpochSecond();
        String key  = path + "|" + ip;

        long[] entry = attempts.compute(key, (k, existing) -> {
            if (existing == null) {
                return new long[]{ 1L, now };
            }
            long windowStart = existing[1];
            // Expired window → reset
            if (now - windowStart >= WINDOW_SECONDS) {
                return new long[]{ 1L, now };
            }
            existing[0]++;
            return existing;
        });

        long count       = entry[0];
        long windowStart = entry[1];
        long secondsLeft = WINDOW_SECONDS - (now - windowStart);

        if (count > maxAttempts) {
            log.warn("Rate limit exceeded path={} ip={} attempts={} retryAfter={}s",
                    path, ip, count, secondsLeft);

            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(secondsLeft));

            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "Too many requests. Please try again in "
                            + (secondsLeft / 60 + 1) + " minute(s)."
            ));
            return;
        }

        chain.doFilter(request, response);

        // Safety valve — prune expired entries if the map grew too large.
        if (attempts.size() > MAX_ENTRIES) {
            long cutoff = now - WINDOW_SECONDS;
            attempts.entrySet().removeIf(e -> e.getValue()[1] < cutoff);
        }
    }

    private int maxAttemptsFor(String path) {
        for (LimitRule rule : RULES) {
            if (rule.path().equals(path)) return rule.maxAttempts();
        }
        return Integer.MAX_VALUE;
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Respect X-Forwarded-For set by Render's load balancer
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
