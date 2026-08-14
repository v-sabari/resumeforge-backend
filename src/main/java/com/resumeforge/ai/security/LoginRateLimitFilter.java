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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AUTH-02 FIX: Brute-force protection on POST /api/auth/login.
 *
 * Algorithm: per-IP sliding window counter.
 *   - Window:       15 minutes
 *   - Max attempts: 5 failed logins per window
 *   - On exceed:    HTTP 429 + Retry-After header (seconds until window resets)
 *   - On success:   counter reset is handled by the window expiry naturally
 *
 * This filter runs BEFORE Spring Security so it can block requests before any
 * password hashing work is done (prevents timing-based enumeration under load).
 *
 * Memory: entries expire when the window passes. The map is bounded to
 * MAX_ENTRIES to prevent OOM under targeted attacks.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    private static final String LOGIN_PATH     = "/api/auth/login";
    private static final int    MAX_ATTEMPTS   = 5;
    private static final long   WINDOW_SECONDS = 15 * 60L;   // 15 minutes
    private static final int    MAX_ENTRIES    = 10_000;      // cap memory usage

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Per-IP: [failureCount, windowStartEpochSeconds] */
    private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit POST /api/auth/login
        return !("POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String ip = resolveClientIp(request);
        long   now = Instant.now().getEpochSecond();

        long[] entry = attempts.compute(ip, (key, existing) -> {
            if (existing == null) {
                return new long[]{ 0L, now };
            }
            long windowStart = existing[1];
            // Expired window → reset
            if (now - windowStart >= WINDOW_SECONDS) {
                return new long[]{ 0L, now };
            }
            return existing;
        });

        long failureCount = entry[0];
        long windowStart  = entry[1];
        long secondsLeft  = WINDOW_SECONDS - (now - windowStart);

        if (failureCount >= MAX_ATTEMPTS) {
            log.warn("Login rate-limit exceeded for IP={}, failures={}, retryAfter={}s",
                    ip, failureCount, secondsLeft);

            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(secondsLeft));

            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "Too many login attempts. Please try again in "
                            + (secondsLeft / 60 + 1) + " minute(s)."
            ));
            return;
        }

        // Wrap the response to detect a failed login (4xx from AuthController)
        StatusCapturingResponseWrapper wrapper = new StatusCapturingResponseWrapper(response);
        chain.doFilter(request, wrapper);

        int status = wrapper.getStatus();
        if (status == 401 || status == 403) {
            // Failed login — increment counter
            attempts.compute(ip, (key, existing) -> {
                if (existing == null) return new long[]{ 1L, now };
                existing[0]++;
                return existing;
            });
            // Prune if map is too large (simple safety valve)
            if (attempts.size() > MAX_ENTRIES) {
                long cutoff = now - WINDOW_SECONDS;
                attempts.entrySet().removeIf(e -> e.getValue()[1] < cutoff);
            }
        } else if (status == 200) {
            // Successful login — clear the failure counter for this IP
            attempts.remove(ip);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Respect X-Forwarded-For set by Render's load balancer
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // -------------------------------------------------------------------------
    // Inner class — captures response status without committing the response
    // -------------------------------------------------------------------------

    private static class StatusCapturingResponseWrapper
            extends jakarta.servlet.http.HttpServletResponseWrapper {

        private int status = 200;

        StatusCapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            super.sendError(sc, msg);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
            super.sendError(sc);
        }

        // FIX: Jakarta Servlet HttpServletResponse declares getStatus() as public.
        // Overriding with package-private access (no modifier) is a compile error:
        // "attempting to assign weaker access privileges; was public"
        @Override
        public int getStatus() {
            return status;
        }
    }
}