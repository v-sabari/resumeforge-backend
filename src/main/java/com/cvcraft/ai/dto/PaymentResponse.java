package com.cvcraft.ai.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record PaymentResponse(Long id, String paymentId, BigDecimal amount, String status, LocalDateTime createdAt) {}
