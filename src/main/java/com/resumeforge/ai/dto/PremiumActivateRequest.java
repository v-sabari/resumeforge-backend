package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PremiumActivateRequest {
    @NotNull(message = "Payment ID is required")
    private Long paymentId;
}
