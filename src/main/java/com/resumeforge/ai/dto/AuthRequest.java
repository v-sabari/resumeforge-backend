package com.resumeforge.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    // SEC FIX: mirror RegisterRequest — BCrypt truncates at 72 bytes, so a longer
    // password would only ever be compared against its first 72 bytes.
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
    private String password;

    // REMEMBER-ME: optional boolean. When true the backend issues a JWT (and
    // matching httpOnly cookie) with the longer remember-me lifetime instead of
    // the regular session lifetime. Never trusted as the sole gate — the
    // backend cannot be bypassed into a longer session, and logout stamps the
    // user's token_issued_at watermark so a long-lived token is still revocable
    // server-side. Defaults to false when absent.
    private Boolean rememberMe;
}
