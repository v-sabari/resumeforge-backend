package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Input for LinkedIn profile optimization.
 * Free: 1 per day. Premium: unlimited.
 */
public record LinkedInRequest(
        String currentRole,
        String targetRole,
        String currentHeadline,
        String currentAbout,
        List<String> topSkills,
        List<String> achievements
) {}
