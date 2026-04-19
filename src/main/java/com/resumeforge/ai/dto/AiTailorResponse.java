package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Result of job-specific resume tailoring.
 *
 * tailoredSummary       — rewritten summary targeting the JD
 * tailoredBulletGroups  — rewritten bullets per experience entry (same order as request)
 * suggestedSkillsToAdd  — skills from the JD worth adding to the resume
 * keywordsMissing       — important JD keywords not yet in the resume
 */
public record AiTailorResponse(
        String tailoredSummary,
        List<List<String>> tailoredBulletGroups,
        List<String> suggestedSkillsToAdd,
        List<String> keywordsMissing
) {}
