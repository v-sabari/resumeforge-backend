package com.resumeforge.ai.dto;

import java.util.List;

public record AiSkillsRequest(
        String targetRole,
        List<String> currentSkills,
        List<String> experienceKeywords,
        List<String> projectKeywords
) {
}
