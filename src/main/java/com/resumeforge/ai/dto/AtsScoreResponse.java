package com.resumeforge.ai.dto;

import java.util.List;

/**
 * ATS analysis result.
 *
 * score         — 0 to 100. 75+ is good, 90+ is excellent.
 * grade         — Human-readable grade: "Excellent", "Good", "Fair", "Needs Work"
 * matchedKeywords  — Keywords from the JD (or common ATS terms) found in the resume
 * missingKeywords  — Important keywords not found in the resume
 * topFixes      — Ordered list of the highest-impact improvements (max 5)
 * summary       — 2-sentence overall assessment
 */
public record AtsScoreResponse(
        int score,
        String grade,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        List<String> topFixes,
        String summary
) {}
