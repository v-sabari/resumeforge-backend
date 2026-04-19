package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Input for cover letter generation.
 * Premium-only endpoint.
 */
public record CoverLetterRequest(
        String candidateName,
        String targetRole,
        String companyName,
        String summary,
        List<String> topAchievements,
        List<String> skills,
        /** The job description to tailor the letter to. */
        String jobDescription,
        /**
         * Tone: "professional" | "enthusiastic" | "formal" | "conversational"
         * Defaults to "professional" if null/blank.
         */
        String tone
) {}
