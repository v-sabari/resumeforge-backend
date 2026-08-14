package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Token is required")
    // SEC FIX: cap token length to avoid shipping a gigantic request body into
    // the SHA-256 hasher. (Stored tokens are 64 hex chars; this is generous.)
    @Size(max = 512, message = "Token is invalid")
    private String token;

    @NotBlank(message = "New password is required")
    // SEC FIX: mirror RegisterRequest — BCrypt truncates at 72 bytes.
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
    private String newPassword;
}
