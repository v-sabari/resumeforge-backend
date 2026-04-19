package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Input for interview preparation.
 * Premium-only endpoint.
 */
public record InterviewPrepRequest(
        String targetRole,
        String companyName,
        String summary,
        List<String> skills,
        List<String> topAchievements,
        /** Optional. If provided, generates JD-specific questions. */
        String jobDescription
) {}
