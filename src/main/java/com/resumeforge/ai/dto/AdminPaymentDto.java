package com.resumeforge.ai.dto;

import java.math.BigDecimal;
import java.time.Instant;

// ── Payment history entry ──────────────────────────────────────────────────────
public record AdminPaymentDto(
        Long id,
        String internalPaymentId,
        String razorpayPaymentId,
        Long userId,
        String userEmail,
        BigDecimal amount,
        String status,
        Instant createdAt,
        Instant capturedAt
) {
}
