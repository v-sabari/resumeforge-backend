package com.resumeforge.ai.dto;

/**
 * HIGH-01 FIX: DTO updated to match the relabelled use-case data shape.
 *
 * Changes from CRITICAL-01 version:
 *  - authorName → name       (shorter, consistent with frontend USE_CASES)
 *  - authorRole → role       (shorter, consistent with frontend USE_CASES)
 *  - quote      → story      (honest framing — a use case story, not a user quote)
 *  - rating     removed      (all-5-star unverified ratings are an AdSense thin-content signal)
 *  - outcome    added        (concrete measurable result per use case)
 */
public record TestimonialDto(
        String name,
        String role,
        String story,
        String outcome
) {}