package com.cvcraft.ai.controller;

import com.cvcraft.ai.service.RazorpayWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Razorpay Webhook Controller.
 *
 * CRITICAL: This endpoint must be PUBLIC (no JWT auth) because Razorpay
 * calls it from their servers. Security is provided by HMAC-SHA256 signature
 * verification using the raw request body.
 *
 * Configure in Razorpay Dashboard:
 *   Webhook URL: https://your-backend.onrender.com/api/webhooks/razorpay
 *   Events: payment.captured, order.paid
 *   Secret: same value as RAZORPAY_WEBHOOK_SECRET env var
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RazorpayWebhookService webhookService;

    public WebhookController(RazorpayWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Razorpay sends webhooks as raw JSON body with X-Razorpay-Signature header.
     * We MUST read the raw body string BEFORE any JSON parsing to verify HMAC.
     */
    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, String>> razorpay(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("Razorpay webhook received, signature present: {}", signature != null);

        if (!webhookService.isSignatureValid(rawBody, signature)) {
            log.warn("Invalid Razorpay webhook signature — rejecting");
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid signature"));
        }

        // Process asynchronously-safe (idempotent)
        webhookService.processWebhook(rawBody);

        // Always return 200 after signature verification — Razorpay retries on non-2xx
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
