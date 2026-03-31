package com.resumeforge.ai.dto;

public record PremiumStatusResponse(
        boolean premium,
        String plan,
        String message
) {
}
