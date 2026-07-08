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

@RestController
public class ScreenshotController {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotController.class);
    private static final int TIMEOUT_MS = 10_000;

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
        try {
            String puppeteerUrl = "https://puppeteer-service.onrender.com/screenshot?url=" + url;
            byte[] screenshot = restTemplate.getForObject(puppeteerUrl, byte[].class);
            if (screenshot == null || screenshot.length == 0) {
                log.warn("Puppeteer returned empty response for url={}", url);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(screenshot);
        } catch (RestClientException ex) {
            log.error("Screenshot service error url={}: {}", url, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}