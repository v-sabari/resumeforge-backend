package com.resumeforge.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Razorpay webhook event payload.
 *
 * Razorpay POSTs to /api/payments/webhook with:
 *   Header: X-Razorpay-Signature = HMAC_SHA256(rawBody, webhookSecret)
 *   Body:   JSON matching this structure
 *
 * We handle:
 *   event = "payment.captured"   → grant premium
 *   event = "payment.failed"     → mark FAILED
 *   All others are ignored with 200 OK (Razorpay requires 200 to stop retrying).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPaymentPayload(
        String event,
        Payload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            PaymentEntity payment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentEntity(
            Entity entity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entity(
            String id,
            @JsonProperty("order_id")  String orderId,
            @JsonProperty("contact")   String contact,
            @JsonProperty("email")     String email,
            @JsonProperty("amount")    Long amountPaise,   // Razorpay uses paise (1 INR = 100 paise)
            @JsonProperty("currency")  String currency,
            @JsonProperty("status")    String status,
            @JsonProperty("notes")     Notes notes
    ) {
        /** Amount in major currency units (INR). */
        public BigDecimal amountInRupees() {
            return amountPaise == null
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(amountPaise).movePointLeft(2);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notes(
            @JsonProperty("user_id") String userId
    ) {}
}
