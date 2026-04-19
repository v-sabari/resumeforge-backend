package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Internal idempotency key generated at order-creation time.
     * Format: "pay_" + 18 random hex chars.
     * This is NOT the Razorpay payment ID.
     */
    @Column(name = "payment_id", nullable = false, unique = true, length = 120)
    private String paymentId;

    /**
     * The actual Razorpay payment ID (e.g. "pay_ABC123XYZ").
     * Populated when Razorpay confirms capture via webhook or verify endpoint.
     * Null until then.
     */
    @Column(name = "razorpay_payment_id", unique = true, length = 120)
    private String razorpayPaymentId;

    /**
     * Razorpay order ID or payment link ID associated with this payment.
     * Used for deduplication in webhook processing.
     */
    @Column(name = "razorpay_order_id", length = 120)
    private String razorpayOrderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Lifecycle: CREATED → PAID | FAILED
     * Set ONLY by server-side verification (HMAC or webhook). Never by client.
     */
    @Column(nullable = false, length = 40)
    private String status;

    /** Timestamp when Razorpay confirmed the payment. Null until captured. */
    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
