package com.resumeforge.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        /**
         * Optional referral code from another user's share link.
         * Format: 8 uppercase alphanumeric chars.
         * Silently ignored if invalid (non-existent code doesn't fail registration).
         */
        String referralCode
) {}
