package com.resumeforge.ai.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String paymentId,
        BigDecimal amount,
        String status,
        Instant createdAt
) {
}
