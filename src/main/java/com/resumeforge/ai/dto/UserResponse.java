package com.resumeforge.ai.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        boolean isPremium,
        boolean emailVerified,
        Instant createdAt,
        /** "USER" or "ADMIN" — used by frontend to show/hide admin nav link. */
        String role,
        /** The user's own referral code — displayed in the referral hub. */
        String referralCode
) {}
