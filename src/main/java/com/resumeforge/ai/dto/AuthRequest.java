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
}
