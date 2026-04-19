package com.resumeforge.ai.service;

import com.resumeforge.ai.config.RazorpayProperties;
import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.Payment;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.PaymentRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("99.00");

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final RazorpayProperties razorpayProperties;

    public PaymentService(PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          RazorpayProperties razorpayProperties) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.razorpayProperties = razorpayProperties;
    }

    // ─────────────────────────────────────────────────────────────────
    // Create payment intent
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    public PaymentCreateResponse create(User user, PaymentCreateRequest request) {
        if (user.isPremium()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your account already has Premium access.");
        }

        BigDecimal amount = (request != null && request.amount() != null)
                ? request.amount()
                : DEFAULT_AMOUNT;

        String internalPaymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);

        Payment payment = Payment.builder()
                .user(user)
                .paymentId(internalPaymentId)
                .amount(amount)
                .status("CREATED")
                .build();

        paymentRepository.save(payment);

        return new PaymentCreateResponse(
                internalPaymentId,
                amount,
                "CREATED",
                razorpayProperties.paymentLink(),
                razorpayProperties.keyId(),
                "Redirect the user to the payment link to complete the upgrade."
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Client-side verify (after Payment Link redirect callback)
    //
    // Razorpay appends to the callback URL:
    //   ?razorpay_payment_id=pay_XXX
    //   &razorpay_payment_link_id=plink_XXX
    //   &razorpay_payment_link_reference_id=REF_XXX
    //   &razorpay_payment_link_status=paid
    //   &razorpay_signature=HMAC_SHA256(...)
    //
    // Signature message = payment_link_id|payment_link_reference_id|payment_link_status|payment_id
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse verify(User user, PaymentVerifyRequest request) {
        validateVerifyRequest(request);

        // Build the message Razorpay signed
        String message = request.razorpayPaymentLinkId()
                + "|" + request.razorpayPaymentLinkReferenceId()
                + "|" + request.razorpayPaymentLinkStatus()
                + "|" + request.razorpayPaymentId();

        verifyHmacSignature(message, request.razorpaySignature(), razorpayProperties.keySecret(),
                "Razorpay signature verification failed. Payment not confirmed.");

        // Idempotency: if this Razorpay payment ID is already PAID, return current state
        paymentRepository.findByRazorpayPaymentId(request.razorpayPaymentId())
                .filter(p -> "PAID".equals(p.getStatus()))
                .ifPresent(existingPaid -> {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "This payment has already been processed.");
                });

        // Find our internal record by payment_link_reference_id (= our internal payment ID)
        Payment payment = paymentRepository
                .findByPaymentIdAndUser(request.razorpayPaymentLinkReferenceId(), user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Payment record not found. Please contact support."));

        if ("PAID".equals(payment.getStatus())) {
            return toResponse(payment);
        }

        payment.setRazorpayPaymentId(request.razorpayPaymentId());
        payment.setStatus("PAID");
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);

        grantPremium(user);

        log.info("Premium granted via client verify: userId={} razorpayPaymentId={}",
                user.getId(), request.razorpayPaymentId());

        return toResponse(payment);
    }

    // ─────────────────────────────────────────────────────────────────
    // Webhook handler (called by PaymentsController — raw body, no JWT)
    //
    // Razorpay webhook events handled:
    //   payment.captured → grant premium
    //   payment.failed   → mark FAILED
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    public void processWebhook(String rawBody, String razorpaySignatureHeader) {
        if (razorpaySignatureHeader == null || razorpaySignatureHeader.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing X-Razorpay-Signature header.");
        }

        String webhookSecret = razorpayProperties.webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Razorpay webhook secret is not configured. Rejecting webhook.");
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Webhook secret not configured.");
        }

        verifyHmacSignature(rawBody, razorpaySignatureHeader, webhookSecret,
                "Invalid webhook signature. Event rejected.");

        // Webhook is authentic — parse the payload
        WebhookPaymentPayload webhookPayload = parseWebhookPayload(rawBody);
        String event = webhookPayload.event();

        if (event == null) {
            log.warn("Razorpay webhook received with null event field — ignoring.");
            return;
        }

        switch (event) {
            case "payment.captured" -> handlePaymentCaptured(webhookPayload);
            case "payment.failed"   -> handlePaymentFailed(webhookPayload);
            default -> log.debug("Razorpay webhook event '{}' not handled — ignoring.", event);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Webhook event handlers
    // ─────────────────────────────────────────────────────────────────

    private void handlePaymentCaptured(WebhookPaymentPayload webhookPayload) {
        WebhookPaymentPayload.Entity entity = extractEntity(webhookPayload);
        String razorpayPaymentId = entity.id();

        // Idempotency: skip if already processed
        if (paymentRepository.findByRazorpayPaymentId(razorpayPaymentId)
                .filter(p -> "PAID".equals(p.getStatus()))
                .isPresent()) {
            log.info("Webhook payment.captured already processed: razorpayPaymentId={}", razorpayPaymentId);
            return;
        }

        // Find user by email from webhook payload
        String email = entity.email();
        if (email == null || email.isBlank()) {
            log.error("Webhook payment.captured: no email in payload for razorpayPaymentId={}", razorpayPaymentId);
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            log.error("Webhook payment.captured: no user found for email={}", email);
            return;
        }

        // Find or create a payment record
        Payment payment = paymentRepository
                .findByRazorpayPaymentId(razorpayPaymentId)
                .orElseGet(() -> {
                    String internalId = "whpay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                    return Payment.builder()
                            .user(user)
                            .paymentId(internalId)
                            .razorpayPaymentId(razorpayPaymentId)
                            .razorpayOrderId(entity.orderId())
                            .amount(entity.amountInRupees())
                            .status("CREATED")
                            .build();
                });

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setStatus("PAID");
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);

        grantPremium(user);

        log.info("Premium granted via webhook: userId={} razorpayPaymentId={}",
                user.getId(), razorpayPaymentId);
    }

    private void handlePaymentFailed(WebhookPaymentPayload webhookPayload) {
        WebhookPaymentPayload.Entity entity = extractEntity(webhookPayload);
        String razorpayPaymentId = entity.id();

        paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).ifPresent(payment -> {
            if (!"PAID".equals(payment.getStatus())) {
                payment.setStatus("FAILED");
                paymentRepository.save(payment);
                log.info("Payment marked FAILED via webhook: razorpayPaymentId={}", razorpayPaymentId);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // Payment history
    // ─────────────────────────────────────────────────────────────────

    public List<PaymentResponse> history(User user) {
        return paymentRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    private void grantPremium(User user) {
        if (!user.isPremium()) {
            user.setPremium(true);
            userRepository.save(user);
        }
    }

    /**
     * Verifies an HMAC-SHA256 signature in constant time.
     * Throws ApiException with the provided error message if verification fails.
     */
    private void verifyHmacSignature(String message, String providedSignature,
                                     String secret, String errorMessage) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] expectedBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expectedBytes);

            // Constant-time comparison to prevent timing attacks
            if (!constantTimeEquals(expectedHex, providedSignature)) {
                log.warn("HMAC signature mismatch. Expected={} Provided={}",
                        expectedHex, providedSignature);
                throw new ApiException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("HMAC verification error", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Signature verification failed due to internal error.");
        }
    }

    /**
     * Constant-time string comparison. Prevents timing side-channel attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    private void validateVerifyRequest(PaymentVerifyRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        if (isBlank(request.razorpayPaymentId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "razorpayPaymentId is required.");
        }
        if (isBlank(request.razorpayPaymentLinkId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "razorpayPaymentLinkId is required.");
        }
        if (isBlank(request.razorpayPaymentLinkReferenceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "razorpayPaymentLinkReferenceId is required.");
        }
        if (isBlank(request.razorpayPaymentLinkStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "razorpayPaymentLinkStatus is required.");
        }
        if (isBlank(request.razorpaySignature())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "razorpaySignature is required.");
        }
        if (!"paid".equalsIgnoreCase(request.razorpayPaymentLinkStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Payment status is not 'paid'. No premium granted.");
        }
    }

    private WebhookPaymentPayload parseWebhookPayload(String rawBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(rawBody, WebhookPaymentPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse Razorpay webhook payload: {}", rawBody, e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid webhook payload.");
        }
    }

    private WebhookPaymentPayload.Entity extractEntity(WebhookPaymentPayload payload) {
        if (payload.payload() == null
                || payload.payload().payment() == null
                || payload.payload().payment().entity() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Webhook payload missing payment entity.");
        }
        return payload.payload().payment().entity();
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
