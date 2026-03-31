package com.resumeforge.ai.dto;

public record PaymentVerifyRequest(
        String paymentId,
        String razorpayPaymentId,
        String razorpaySignature,
        String status
) {
}
