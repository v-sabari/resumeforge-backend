package com.resumeforge.ai.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.resumeforge.ai.entity.Payment;
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

    // WEBHOOK-AMOUNT FIX: this used to hardcode 74900, while
    // PaymentService.createPaymentOrder() reads the real charged amount from
    // this same configurable property (payment.amount, env var
    // PAYMENT_AMOUNT). If the price is ever changed via that env var, orders
    // are created at the new price but this handler would still compare
    // against the old hardcoded value and silently drop every
    // payment.captured webhook — breaking the webhook fallback for premium
    // activation and invoice emails with no visible error anywhere. Reading
    // from the same property both places eliminates that landmine.
    @Value("${payment.amount}")
    private int configuredAmountPaise;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {

        // 1. Signature check
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.status(400).body("Missing signature");
        }

        String expectedSignature = new HmacUtils("HmacSHA256", webhookSecret)
                .hmacHex(rawBody);

        if (!expectedSignature.equals(signature)) {
            return ResponseEntity.status(400).body("Invalid signature");
        }

        try {
            JsonNode event = objectMapper.readTree(rawBody);
            String eventType = event.path("event").asString("");

            switch (eventType) {
                case "payment.captured" -> handlePaymentCaptured(event);
                case "payment.failed" -> handlePaymentFailed(event);
                default -> log.info("Ignored event: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Webhook parsing failed", e);
            return ResponseEntity.status(400).body("Invalid payload");
        }

        return ResponseEntity.ok("OK");
    }

    // =========================
    // PAYMENT SUCCESS HANDLER
    // =========================
    private void handlePaymentCaptured(JsonNode event) {

        JsonNode paymentEntity = event.path("payload").path("payment").path("entity");

        String orderId = paymentEntity.path("order_id").asString(null);
        String paymentId = paymentEntity.path("id").asString(null);
        int amount = paymentEntity.path("amount").asInt(0);

        if (orderId == null || paymentId == null) return;

        // 🔥 SECURITY CHECK: amount must match the currently configured price.
        // WEBHOOK-AMOUNT FIX: was hardcoded to 74900 — see field doc comment above.
        if (amount != configuredAmountPaise) {
            log.warn("Invalid payment amount detected: {} (expected {})", amount, configuredAmountPaise);
            return;
        }

        paymentRepository.findByRazorpayOrderId(orderId).ifPresent(payment -> {

            // 🔥 IDEMPOTENCY CHECK (NO DUPLICATE PROCESSING)
            if (paymentId.equals(payment.getRazorpayPaymentId())
                    && "COMPLETED".equals(payment.getStatus())) {
                return;
            }

            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus("COMPLETED");
            payment.setPaymentMethod(
                    paymentEntity.path("method").asString("unknown")
            );

            paymentRepository.save(payment);

            // Activate premium user
            userRepository.findById(payment.getUserId()).ifPresent(user -> {

                user.setPremium(true);
                userRepository.save(user);

                log.info("Premium activated for userId={}", user.getId());

                // 🔥 EMAIL (NON-BLOCKING IDEA: safe direct call here,
                // but ideally @Async in EmailService)
                try {
                    emailService.sendInvoiceEmail(user.getEmail(), payment);
                    payment.setInvoiceSent(true);
                    paymentRepository.save(payment);
                } catch (Exception e) {
                    log.error("Invoice email failed", e);
                }
            });
        });
    }

    // =========================
    // PAYMENT FAILED HANDLER
    // =========================
    private void handlePaymentFailed(JsonNode event) {

        JsonNode paymentEntity = event.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asString(null);

        if (orderId == null) return;

        paymentRepository.findByRazorpayOrderId(orderId).ifPresent(payment -> {

            if (!"COMPLETED".equals(payment.getStatus())) {
                payment.setStatus("FAILED");
                paymentRepository.save(payment);
                log.info("Payment marked FAILED for orderId={}", orderId);
            }
        });
    }
}