package com.resumeforge.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    // SEC FIX: BCrypt silently truncates input at 72 bytes — cap the length so a
    // user cannot register with a long password that then behaves unexpectedly.
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
    private String password;

    private String referralCode;
}
