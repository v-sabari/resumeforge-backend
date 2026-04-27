package com.resumeforge.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.repository.PaymentRepository;
import com.resumeforge.ai.repository.UserRepository;
import com.resumeforge.ai.service.EmailService;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Handles Razorpay webhook callbacks.
 * This endpoint is intentionally unauthenticated (no JWT required) because
 * Razorpay is an external service. Security is enforced by verifying the
 * X-Razorpay-Signature HMAC-SHA256 header against the webhook secret.
 *
 * Permitted in SecurityConfig: .requestMatchers("/api/payments/webhook").permitAll()
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Value("${app.razorpay.webhook-secret}")
    private String webhookSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        // 1. Reject immediately if the signature header is missing
        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook received without X-Razorpay-Signature header");
            return ResponseEntity.status(400).body("Missing signature");
        }

        // 2. Verify HMAC-SHA256 signature
        String expectedSignature = new HmacUtils("HmacSHA256", webhookSecret.getBytes(StandardCharsets.UTF_8))
                .hmacHex(rawBody);

        if (!expectedSignature.equals(signature)) {
            log.warn("Razorpay webhook signature mismatch — possible spoofed request");
            return ResponseEntity.status(400).body("Invalid signature");
        }

        // 3. Parse the event and route to the appropriate handler
        try {
            JsonNode event = objectMapper.readTree(rawBody);
            String eventType = event.path("event").asText("");

            log.info("Razorpay webhook received: event={}", eventType);

            switch (eventType) {
                case "payment.captured" -> handlePaymentCaptured(event);
                case "payment.failed"   -> handlePaymentFailed(event);
                default -> log.debug("Unhandled Razorpay event type: {}", eventType);
            }

        } catch (Exception e) {
            // Return 200 anyway — Razorpay retries on non-2xx, so a parse error
            // should not cause an infinite retry loop for a permanently bad payload.
            log.error("Error processing Razorpay webhook payload", e);
        }

        return ResponseEntity.ok("OK");
    }

    private void handlePaymentCaptured(JsonNode event) {
        JsonNode paymentEntity = event.path("payload").path("payment").path("entity");
        String razorpayOrderId  = paymentEntity.path("order_id").asText(null);
        String razorpayPaymentId = paymentEntity.path("id").asText(null);

        if (razorpayOrderId == null || razorpayPaymentId == null) {
            log.warn("payment.captured event missing order_id or payment id — skipping");
            return;
        }

        paymentRepository.findByRazorpayOrderId(razorpayOrderId).ifPresentOrElse(payment -> {
            if ("COMPLETED".equals(payment.getStatus())) {
                log.info("payment.captured: order {} already COMPLETED — idempotent skip", razorpayOrderId);
                return;
            }

            payment.setRazorpayPaymentId(razorpayPaymentId);
            payment.setStatus("COMPLETED");
            paymentRepository.save(payment);

            // Auto-activate premium and send invoice
            userRepository.findById(payment.getUserId()).ifPresent(user -> {
                user.setPremium(true);
                userRepository.save(user);

                if (!payment.isInvoiceSent()) {
                    emailService.sendInvoiceEmail(user.getEmail(), payment);
                    payment.setInvoiceSent(true);
                    paymentRepository.save(payment);
                }

                log.info("payment.captured: premium activated for userId={}", user.getId());
            });

        }, () -> log.warn("payment.captured: no payment record found for orderId={}", razorpayOrderId));
    }

    private void handlePaymentFailed(JsonNode event) {
        JsonNode paymentEntity = event.path("payload").path("payment").path("entity");
        String razorpayOrderId = paymentEntity.path("order_id").asText(null);

        if (razorpayOrderId == null) {
            log.warn("payment.failed event missing order_id — skipping");
            return;
        }

        paymentRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(payment -> {
            if (!"COMPLETED".equals(payment.getStatus())) {
                payment.setStatus("FAILED");
                paymentRepository.save(payment);
                log.info("payment.failed: marked orderId={} as FAILED", razorpayOrderId);
            }
        });
    }
}