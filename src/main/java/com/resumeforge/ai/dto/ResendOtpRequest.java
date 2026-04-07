package com.resumeforge.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendOtpRequest(
        @NotBlank
        @Email
        @Size(max = 160)
        String email
) {
}