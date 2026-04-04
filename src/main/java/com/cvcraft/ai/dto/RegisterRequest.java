package com.cvcraft.ai.dto;
import jakarta.validation.constraints.*;
public record RegisterRequest(
    @NotBlank @Size(min=2, max=120) String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min=6, max=100) String password
) {}
