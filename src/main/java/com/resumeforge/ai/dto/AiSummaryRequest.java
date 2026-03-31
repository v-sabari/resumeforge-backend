package com.resumeforge.ai.dto;

import java.util.List;

public record AiSummaryRequest(
        String currentSummary,
        String targetRole,
        List<String> skills,
        List<String> achievements,
        List<String> highlights
) {
}
