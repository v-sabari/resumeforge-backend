package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI-01 FIX: Health check for the AI subsystem.
 *
 * GET /api/ai/health — accessible to authenticated users.
 * GET /api/health/ai — same endpoint, alternate path (no auth required,
 *   used by Render uptime checks and deployment verification scripts).
 *
 * Returns:
 *   200 { configured: true,  model: "...", keyConfigured: true  } — ready
 *   200 { configured: false, model: "...", keyConfigured: false } — degraded
 *
 * Does NOT make a live OpenRouter API call (avoids cost + latency).
 * To test actual connectivity use the Postman "AI Connectivity Test" collection.
 *
 * The API key is NEVER included in the response — only whether it is configured.
 */
@RestController
public class AiHealthController {

    private static final Logger log = LoggerFactory.getLogger(AiHealthController.class);

    @Value("${app.openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${app.openrouter.model:unknown}")
    private String model;

    @Value("${app.openrouter.base-url:}")
    private String openRouterBaseUrl;

    @GetMapping({"/api/ai/health", "/api/health/ai"})
    public ResponseEntity<Map<String, Object>> aiHealth() {
        boolean keyConfigured = openRouterApiKey != null && !openRouterApiKey.isBlank();
        boolean urlConfigured = openRouterBaseUrl != null && !openRouterBaseUrl.isBlank();
        boolean configured    = keyConfigured && urlConfigured;

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service",       "AI / OpenRouter");
        status.put("configured",    configured);
        status.put("model",         model);
        status.put("keyConfigured", keyConfigured);
        status.put("urlConfigured", urlConfigured);

        if (!configured) {
            log.warn("[AiHealth] Health check failed: keyConfigured={}, urlConfigured={}",
                    keyConfigured, urlConfigured);
            status.put("error",
                    "OPENROUTER_API_KEY or base URL is not set. " +
                            "Set OPENROUTER_API_KEY in Render Environment Variables.");
        }

        // Always return 200 — Render health checks should not fail due to
        // AI misconfiguration (the app is still running, just AI is degraded).
        return ResponseEntity.ok(status);
    }
}