package com.cvcraft.ai.service;

import com.cvcraft.ai.config.RazorpayProperties;
import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.entity.*;
import com.cvcraft.ai.exception.ApiException;
import com.cvcraft.ai.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository  paymentRepository;
    private final RazorpayProperties razorpayProperties;

    /**
     * NOTE: UserRepository is intentionally NOT injected here.
     *
     * SECURITY: Premium activation is EXCLUSIVELY performed by RazorpayWebhookService
     * after HMAC-SHA256 signature verification of the Razorpay webhook.
     * The verify() endpoint must NEVER set user.premium = true directly — that was
     * the critical security vulnerability that was fixed.
     */
    public PaymentService(PaymentRepository paymentRepository,
                          RazorpayProperties razorpayProperties) {
        this.paymentRepository  = paymentRepository;
        this.razorpayProperties = razorpayProperties;
    }

    /**
     * Creates an internal payment record and returns the Razorpay payment link.
     * The internal paymentId (pay_xxx) MUST be embedded in the Razorpay Payment Link's
     * notes field (key: internal_payment_id) so the webhook can match it after payment.
     */
    @Transactional
    public PaymentCreateResponse create(User user, PaymentCreateRequest request) {
        BigDecimal amount = (request != null && request.amount() != null)
                ? request.amount()
                : new BigDecimal("9.00");

        String paymentId = "pay_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 18);

        Payment payment = Payment.builder()
                .user(user)
                .paymentId(paymentId)
                .amount(amount)
                .status("CREATED")
                .build();
        paymentRepository.save(payment);

        return new PaymentCreateResponse(
                paymentId,
                amount,
                payment.getStatus(),
                razorpayProperties.paymentLink(),
                razorpayProperties.keyId(),
                "Payment created. Redirect user to the payment link to complete upgrade."
        );
    }

    /**
     * Records a non-privileged status update from the frontend.
     *
     * SECURITY: Frontend may only set PENDING or FAILED.
     * PAID status is set exclusively by the webhook after signature verification.
     * This endpoint MUST NOT activate premium — that would allow free self-upgrade.
     */
    @Transactional
    public PaymentResponse verify(User user, PaymentVerifyRequest request) {
        if (request == null || request.paymentId() == null || request.paymentId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "paymentId is required");
        }

        Payment payment = paymentRepository.findByPaymentIdAndUser(request.paymentId(), user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment record not found"));

        // Only allow non-privileged status values from the frontend
        String status = (request.status() == null || request.status().isBlank())
                ? "PENDING"
                : request.status().trim().toUpperCase();

        // Block any attempt to self-escalate to PAID — webhook-only
        if ("PAID".equals(status) || "VERIFIED".equals(status)) {
            status = "PENDING";
        }
        if (!List.of("CREATED", "PENDING", "FAILED").contains(status)) {
            status = "PENDING";
        }

        payment.setStatus(status);
        paymentRepository.save(payment);
        return toResponse(payment);
    }

    public List<PaymentResponse> history(User user) {
        return paymentRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getPaymentId(),
                p.getAmount(), p.getStatus(), p.getCreatedAt()
        );
    }
}
