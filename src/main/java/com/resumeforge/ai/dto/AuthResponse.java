package com.resumeforge.ai.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
