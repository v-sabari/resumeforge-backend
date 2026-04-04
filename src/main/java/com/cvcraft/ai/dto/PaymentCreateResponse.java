package com.cvcraft.ai.dto;
import java.math.BigDecimal;
public record PaymentCreateResponse(String paymentId, BigDecimal amount, String status,
    String paymentLink, String keyId, String message) {}
