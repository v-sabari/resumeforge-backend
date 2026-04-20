package com.resumeforge.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentCreateRequest {
    private BigDecimal amount;
}
