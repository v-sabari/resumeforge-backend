package com.resumeforge.ai.dto;

import java.math.BigDecimal;

public record PaymentCreateResponse(
        String paymentId,
        BigDecimal amount,
        String status,
        String paymentLink,
        String razorpayKeyId,
        String message
) {
}
