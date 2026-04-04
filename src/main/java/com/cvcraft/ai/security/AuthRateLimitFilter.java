package com.cvcraft.ai.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory sliding-window rate limiter for auth endpoints.
 * Limits: 10 requests per IP per minute on /api/auth/login and /api/auth/register.
 *
 * For production at scale, replace with Redis-backed rate limiting.
 */
@Component
@Order(1)
public class AuthRateLimitFilter implements Filter {

    private static final int MAX_REQUESTS    = 10;
    private static final long WINDOW_MS      = 60_000L; // 1 minute

    // IP -> [requestCount, windowStartMs]
    private final Map<String, long[]> requestCounts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getServletPath();
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            String ip = getClientIp(request);
            if (isRateLimited(ip)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\"," +
                    "\"message\":\"Too many attempts. Please wait 1 minute before trying again.\"}"
                );
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean isRateLimited(String ip) {
        long now = Instant.now().toEpochMilli();
        long[] state = requestCounts.compute(ip, (k, v) -> {
            if (v == null || now - v[1] > WINDOW_MS) {
                return new long[]{ 1L, now };
            }
            v[0]++;
            return v;
        });
        return state[0] > MAX_REQUESTS;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
