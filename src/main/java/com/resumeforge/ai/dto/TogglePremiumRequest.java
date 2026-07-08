package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TogglePremiumRequest {
    @NotNull(message = "Premium status is required")
    private Boolean premium;
}
