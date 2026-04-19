package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.service.CurrentUserService;
import com.resumeforge.ai.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentsController {

    private static final Logger log = LoggerFactory.getLogger(PaymentsController.class);

    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentsController(PaymentService paymentService, CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    /**
     * Create a payment intent for the authenticated user.
     * Returns the Razorpay Payment Link URL to redirect the user to.
     */
    @PostMapping("/create")
    public PaymentCreateResponse create(@RequestBody(required = false) PaymentCreateRequest request) {
        return paymentService.create(currentUserService.getCurrentUser(), request);
    }

    /**
     * Verify a Razorpay Payment Link completion from the client side.
     *
     * The frontend collects these query params from the Razorpay callback URL and POSTs them here.
     * The backend verifies the HMAC signature before granting premium.
     *
     * Required fields: razorpayPaymentId, razorpayPaymentLinkId,
     *                  razorpayPaymentLinkReferenceId, razorpayPaymentLinkStatus, razorpaySignature
     */
    @PostMapping("/verify")
    public PaymentResponse verify(@RequestBody PaymentVerifyRequest request) {
        return paymentService.verify(currentUserService.getCurrentUser(), request);
    }

    /**
     * Razorpay webhook endpoint.
     *
     * Razorpay POSTs signed events to this URL.
     * Header: X-Razorpay-Signature = HMAC_SHA256(rawBody, webhookSecret)
     *
     * Security:
     *   - No JWT required (Razorpay cannot send a JWT)
     *   - HMAC signature verified against raw request body inside PaymentService
     *   - Returns 200 OK on success (Razorpay retries on non-200)
     *   - Returns 400 if signature is invalid
     *
     * Permitted in SecurityConfig without JWT authentication.
     * CSRF disabled for this endpoint only via SecurityConfig.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        log.debug("Razorpay webhook received: event body preview={}",
                rawBody.length() > 200 ? rawBody.substring(0, 200) + "..." : rawBody);

        paymentService.processWebhook(rawBody, signature);

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /**
     * Payment history for the authenticated user.
     */
    @GetMapping("/history")
    public List<PaymentResponse> history() {
        return paymentService.history(currentUserService.getCurrentUser());
    }
}
