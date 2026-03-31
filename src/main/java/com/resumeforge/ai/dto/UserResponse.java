package com.resumeforge.ai.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        boolean isPremium,
        Instant createdAt
) {
}
