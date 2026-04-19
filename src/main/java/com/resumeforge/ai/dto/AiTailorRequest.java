package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Input for job-specific resume tailoring.
 * Premium-only endpoint.
 *
 * The AI rewrites the summary and up to 3 experience bullet groups
 * to better match the provided job description.
 */
public record AiTailorRequest(
        String targetRole,
        String currentSummary,
        List<String> skills,
        /** Each inner list is the bullets for one experience entry. */
        List<List<String>> experienceBulletGroups,
        /** The job description to tailor against. Required. */
        String jobDescription
) {}
