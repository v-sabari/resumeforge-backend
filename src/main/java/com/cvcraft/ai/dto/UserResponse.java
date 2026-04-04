package com.cvcraft.ai.dto;
import java.time.LocalDateTime;
public record UserResponse(Long id, String name, String email, boolean isPremium, LocalDateTime createdAt) {}
