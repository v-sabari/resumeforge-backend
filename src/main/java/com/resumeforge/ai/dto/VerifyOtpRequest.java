package com.resumeforge.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank
        @Email
        @Size(max = 160)
        String email,

        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        String otp
) {
}