package com.cvcraft.ai.dto;
import jakarta.validation.constraints.*;
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
