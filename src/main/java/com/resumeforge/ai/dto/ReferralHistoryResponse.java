package com.resumeforge.ai.dto;

import java.time.Instant;

public record ReferralHistoryResponse(
        Long referralId,
        String referredUserEmail,
        String status,
        Instant createdAt,
        Instant qualifiedAt
) {
}
