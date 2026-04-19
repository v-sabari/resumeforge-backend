package com.resumeforge.ai.dto;

import java.time.Instant;

public record TestimonialDto(
        Long id,
        String authorName,
        String authorRole,
        String quote,
        int rating,
        boolean approved,
        int displayOrder,
        Instant createdAt
) {}
