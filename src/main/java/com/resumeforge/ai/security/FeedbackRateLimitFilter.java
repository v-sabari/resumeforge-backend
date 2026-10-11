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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit for POST /api/feedback: max 3 submissions per 10 minutes per IP.
 *
 * Mirrors SensitiveEndpointRateLimitFilter (per-IP sliding window, bounded map,
 * 429 + Retry-After) but with its own window/limit so the existing contact /
 * auth limits are untouched. The GET public listing is not throttled here.
 */
public class FeedbackRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FeedbackRateLimitFilter.class);

    private static final String FEEDBACK_PATH  = "/api/feedback";
    private static final int    MAX_ATTEMPTS   = 3;
    private static final long   WINDOW_SECONDS = 10 * 60L;   // 10 minutes
    private static final int    MAX_ENTRIES    = 10_000;     // cap memory usage

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Per-IP: [attemptCount, windowStartEpochSeconds] */
    private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && FEEDBACK_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String ip  = resolveClientIp(request);
        long   now = Instant.now().getEpochSecond();

        long[] entry = attempts.compute(ip, (key, existing) -> {
            if (existing == null) {
                return new long[]{ 1L, now };
            }
            long windowStart = existing[1];
            if (now - windowStart >= WINDOW_SECONDS) {
                return new long[]{ 1L, now };
            }
            existing[0]++;
            return existing;
        });

        long count       = entry[0];
        long windowStart = entry[1];
        long secondsLeft = WINDOW_SECONDS - (now - windowStart);

        if (count > MAX_ATTEMPTS) {
            log.warn("Feedback rate limit exceeded ip={} attempts={} retryAfter={}s",
                    ip, count, secondsLeft);

            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(secondsLeft));

            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "Too many feedback submissions. Please try again in "
                            + (secondsLeft / 60 + 1) + " minute(s)."
            ));
            return;
        }

        chain.doFilter(request, response);

        if (attempts.size() > MAX_ENTRIES) {
            long cutoff = now - WINDOW_SECONDS;
            attempts.entrySet().removeIf(e -> e.getValue()[1] < cutoff);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
