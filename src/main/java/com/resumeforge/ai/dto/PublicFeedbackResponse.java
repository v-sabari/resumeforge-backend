package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Paginated public feedback payload, including the header aggregates shown in
 * the "What Users Say" section (average rating + total count).
 */
public record PublicFeedbackResponse(
        List<PublicFeedbackDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore,
        long totalCount,
        double averageRating
) {}
