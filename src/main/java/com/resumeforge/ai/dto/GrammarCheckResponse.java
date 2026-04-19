package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Grammar and clarity check result.
 *
 * correctedText  — Full corrected version of the input
 * issuesFound    — List of specific issues identified (grammar, clarity, ATS, tone)
 * issueCount     — Total number of issues found (0 = clean)
 * clean          — True if no issues were found
 */
public record GrammarCheckResponse(
        String correctedText,
        List<String> issuesFound,
        int issueCount,
        boolean clean
) {}
