package com.resumeforge.ai.dto;

/**
 * LinkedIn optimization result.
 *
 * optimizedHeadline — LinkedIn headline (max 220 chars), keyword-rich
 * optimizedAbout    — LinkedIn About section (3-4 paragraphs, ~2000 chars max)
 * headlineTips      — Why this headline was chosen / how to customize it
 */
public record LinkedInResponse(
        String optimizedHeadline,
        String optimizedAbout,
        String headlineTips
) {}
