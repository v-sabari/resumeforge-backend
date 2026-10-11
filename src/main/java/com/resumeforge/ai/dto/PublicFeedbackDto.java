package com.resumeforge.ai.dto;

import java.time.Instant;

/**
 * Safe, public projection of a general feedback entry.
 *
 * Deliberately excludes userEmail, pageUrl, userAgent, status and type so the
 * public endpoint can never leak private data or identify a reporter. The
 * author is the first name only, or "Anonymous".
 */
public record PublicFeedbackDto(
        Long id,
        String title,
        String details,
        int rating,
        String author,
        Instant createdAt
) {}
