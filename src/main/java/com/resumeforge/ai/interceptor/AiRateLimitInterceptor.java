package com.resumeforge.ai.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Intercepts all /api/ai/** requests and enforces per-user rate limits.
 *
 * Rate limit: {@value RateLimitService#AI_MAX_REQUESTS_PER_MINUTE} requests per minute per user per endpoint.
 *
 * On limit exceeded:
 *   HTTP 429 Too Many Requests
 *   Header: Retry-After: <seconds until reset>
 *   Body: standard error JSON
 *
 * Registered in SecurityConfig via WebMvcConfigurer.
 */
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public AiRateLimitInterceptor(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            // Let Spring Security handle unauthenticated requests — don't block here
            return true;
        }

        String endpoint = extractEndpointName(request.getRequestURI());
        boolean allowed = rateLimitService.tryConsume(user.getId(), endpoint);

        if (!allowed) {
            long retryAfter = rateLimitService.secondsUntilReset(user.getId(), endpoint);
            writeRateLimitResponse(response, retryAfter);
            return false;
        }

        return true;
    }

    /**
     * Extracts a short endpoint name from the URI for per-endpoint rate limiting.
     * e.g. "/api/ai/summary" → "summary"
     */
    private String extractEndpointName(String uri) {
        if (uri == null) return "unknown";
        int lastSlash = uri.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < uri.length() - 1
                ? uri.substring(lastSlash + 1)
                : uri;
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 429,
                "message", "AI request limit reached. You can make "
                        + RateLimitService.AI_MAX_REQUESTS_PER_MINUTE
                        + " AI requests per minute. Please wait " + retryAfter + " seconds."
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
