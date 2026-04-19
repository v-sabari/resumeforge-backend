package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Input for ATS score analysis.
 *
 * The caller provides the resume content and optionally a job description.
 * If jobDescription is omitted, the AI scores against general ATS best practices.
 * If provided, it also identifies missing keywords specific to that JD.
 */
public record AtsScoreRequest(
        String targetRole,
        String summary,
        List<String> skills,
        List<String> experienceBullets,
        List<String> achievements,
        /** Optional. If provided, enables job-specific keyword matching. */
        String jobDescription
) {}
