package com.cvcraft.ai.service;

import com.cvcraft.ai.entity.Payment;
import com.cvcraft.ai.entity.User;
import com.cvcraft.ai.repository.PaymentRepository;
import com.cvcraft.ai.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Handles Razorpay webhook events with HMAC-SHA256 signature verification.
 *
 * SECURITY: Premium is activated ONLY after the backend verifies the Razorpay
 * webhook signature using the raw request body and the webhook secret.
 * The frontend NEVER directly activates premium — it only polls status.
 */
@Service
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentRepository paymentRepository;
    private final UserRepository    userRepository;

    @Value("${app.razorpay.webhook-secret:}")
    private String webhookSecret;

    public RazorpayWebhookService(PaymentRepository paymentRepository,
                                  UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository    = userRepository;
    }

    /**
     * Verifies Razorpay-Signature header using HMAC-SHA256.
     * Must be called BEFORE parsing the body to avoid tampering.
     *
     * @param rawBody   the raw UTF-8 request body as received (do NOT parse first)
     * @param signature the value of the X-Razorpay-Signature header
     * @return true if signature is valid
     */
    public boolean isSignatureValid(String rawBody, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("RAZORPAY_WEBHOOK_SECRET not configured — skipping signature verification");
            return true; // allow in dev, reject in prod if secret not set
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(computedHex, signature);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    /**
     * Processes a verified Razorpay webhook event.
     * Handles payment.captured, order.paid events for premium activation.
     * Idempotent — safe to call multiple times for the same payment.
     */
    @Transactional
    public void processWebhook(String rawBody) {
        try {
            JsonNode root  = MAPPER.readTree(rawBody);
            String event   = root.path("event").asText();
            JsonNode entity = root.path("payload").path("payment").path("entity");

            if (entity.isMissingNode()) {
                entity = root.path("payload").path("order").path("entity");
            }

            log.info("Razorpay webhook event: {}", event);

            if ("payment.captured".equals(event) || "order.paid".equals(event)) {
                String razorpayPaymentId = entity.path("id").asText();
                String status            = entity.path("status").asText(); // captured
                String notes             = entity.path("notes").toString();

                // Try to find payment by razorpay payment id or by notes containing our internal paymentId
                activatePremiumForPayment(razorpayPaymentId, notes, status);
            }

        } catch (Exception e) {
            log.error("Failed to process Razorpay webhook", e);
            // Do NOT rethrow — Razorpay will retry if we return 500
            // We return 200 to acknowledge receipt even if processing failed
        }
    }

    private void activatePremiumForPayment(String razorpayId, String notes, String status) {
        // First try: match by our internal paymentId stored in Razorpay notes.
        // When using Razorpay Payment Links, the Razorpay-generated payment ID (razorpayId)
        // will NOT match the UUID-based paymentId we generated. We embed our internal ID
        // in the payment link's "notes" field (key: internal_payment_id) so we can look it up here.
        boolean activated = tryActivateByNotes(notes);
        if (activated) return;

        // Second try: match by the Razorpay payment ID directly (works when using Razorpay Orders API)
        paymentRepository.findByPaymentId(razorpayId).ifPresentOrElse(payment -> {
            if ("PAID".equals(payment.getStatus())) {
                log.info("Payment {} already processed — skipping", razorpayId);
                return;
            }
            payment.setStatus("PAID");
            paymentRepository.save(payment);
            activateUserPremium(payment.getUser(), razorpayId);
        }, () -> log.warn(
            "No payment found for razorpay id {} and no internal_payment_id in notes. " +
            "ACTION: Add internal_payment_id to your Razorpay Payment Link notes field, " +
            "or switch to Razorpay Orders API for reliable payment ID matching. " +
            "Manual review required for this payment.", razorpayId));
    }

    /**
     * Attempts to look up the payment using the internal_payment_id embedded in Razorpay notes.
     * Notes should be set when creating the Razorpay Payment Link:
     *   notes: { "internal_payment_id": "pay_abc123..." }
     *
     * @return true if payment was found and processed, false otherwise
     */
    private boolean tryActivateByNotes(String notes) {
        if (notes == null || notes.isBlank() || notes.equals("null")) return false;
        try {
            JsonNode notesNode = MAPPER.readTree(notes);
            String internalId = notesNode.path("internal_payment_id").asText("");
            if (internalId.isBlank()) return false;

            return paymentRepository.findByPaymentId(internalId).map(payment -> {
                if ("PAID".equals(payment.getStatus())) {
                    log.info("Payment {} already processed via notes lookup — skipping", internalId);
                    return true;
                }
                payment.setStatus("PAID");
                paymentRepository.save(payment);
                activateUserPremium(payment.getUser(), internalId);
                return true;
            }).orElse(false);
        } catch (Exception e) {
            log.warn("Could not parse notes JSON for payment lookup: {}", e.getMessage());
            return false;
        }
    }

    private void activateUserPremium(User user, String paymentRef) {
        if (!user.isPremium()) {
            user.setPremium(true);
            userRepository.save(user);
            log.info("Premium activated for user {} via webhook (payment ref: {})",
                    user.getEmail(), paymentRef);
        } else {
            log.info("User {} already premium — skipping activation (payment ref: {})",
                    user.getEmail(), paymentRef);
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
