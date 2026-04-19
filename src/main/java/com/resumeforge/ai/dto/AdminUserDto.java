package com.resumeforge.ai.dto;

import java.time.Instant;

// ── User list entry ────────────────────────────────────────────────────────────
public record AdminUserDto(
        Long id,
        String name,
        String email,
        boolean premium,
        Instant premiumExpiresAt,       // null = lifetime premium
        boolean emailVerified,
        String role,
        String referralCode,
        int resumeCount,
        Instant createdAt
) {
}
