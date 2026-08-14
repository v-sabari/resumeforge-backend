package com.resumeforge.ai.controller;   // ← FIXED: was com.example.controller

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@RestController
public class ScreenshotController {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotController.class);
    private static final int TIMEOUT_MS = 10_000;

    // SEC FIX (SSRF): the url parameter is forwarded to the Puppeteer service,
    // which fetches whatever it is given. Restrict it to the application's own
    // marketing domains so an attacker cannot point the service at internal
    // hosts (e.g. 169.254.169.254 metadata, localhost services) or other sites.
    private static final List<String> ALLOWED_HOSTS = List.of(
            "resumeforgeai.site",
            "www.resumeforgeai.site"
    );

    private final RestTemplate restTemplate;

    public ScreenshotController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    @GetMapping("/api/screenshot")
    public ResponseEntity<byte[]> getScreenshot(
            @RequestParam(defaultValue = "https://www.resumeforgeai.site") String url) {
        String validatedUrl = validateAndSanitizeUrl(url);
        if (validatedUrl == null) {
            log.warn("Screenshot request blocked: disallowed url");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":false,\"message\":\"URL is not allowed.\"}".getBytes(StandardCharsets.UTF_8));
        }
        try {
            // SEC FIX: URL-encode the target so a malicious value cannot alter the
            // query string of the puppeteer service URL itself.
            String puppeteerUrl = "https://puppeteer-service.onrender.com/screenshot?url="
                    + URLEncoder.encode(validatedUrl, StandardCharsets.UTF_8);
            byte[] screenshot = restTemplate.getForObject(puppeteerUrl, byte[].class);
            if (screenshot == null || screenshot.length == 0) {
                log.warn("Puppeteer returned empty response");
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(screenshot);
        } catch (RestClientException ex) {
            log.error("Screenshot service error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private String validateAndSanitizeUrl(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 2048) return null;
        String trimmed = raw.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            if (!isAllowedHost(host)) return null;
            return trimmed;
        } catch (IllegalArgumentException e) {
            // Malformed URI (spaces, bad chars) — reject rather than forward.
            return null;
        }
    }

    private boolean isAllowedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String allowed : ALLOWED_HOSTS) {
            if (normalized.equals(allowed) || normalized.endsWith("." + allowed)) return true;
        }
        return false;
    }
}