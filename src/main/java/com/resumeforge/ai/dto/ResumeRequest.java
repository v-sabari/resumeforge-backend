package com.resumeforge.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    @Pattern(
            regexp = "^(classic|modern|minimal|corporate|traditional|clean|fresher|graduate|tech|engineering|executive|leadership|creative|designer|sleek|contemporary|academic|research|medical|finance)$",
            message = "Template must be one of: classic, modern, minimal, corporate, traditional, clean, fresher, graduate, tech, engineering, executive, leadership, creative, designer, sleek, contemporary, academic, research, medical, finance"
    )
    private String template;

    private String personalInfo;
    private String summary;
    private String experience;
    private String education;
    private String skills;
    private String projects;
    private String certifications;
    private String customSections;
    private String achievements;
    private String languages;

    // V20: ordered section descriptor array —
    // [{id, type, key, label, visible, order}, ...]
    private String sectionsConfig;

    // COMPRESS FEATURE: density scale applied to fit a chosen page count.
    // Bounded server-side too (not just trusted from the client) — never
    // allow a value that would make text unreadable (below the frontend's
    // own MIN_SCALE floor of 0.72) or a nonsensical scale-up above 1.0.
    // COMPRESS FEATURE: density scale applied to fit a chosen page count.
    // Bounded server-side too (not just trusted from the client) — never
    // allow a value that would make text unreadable (below the frontend's
    // own MIN_SCALE floor of 0.72, with a little slack to 0.5) or
    // unprofessionally oversized (above the frontend's own MAX_SCALE
    // ceiling of 1.35). Keep these bounds in sync with
    // frontend/src/utils/compression.js MIN_SCALE / MAX_SCALE.
    @DecimalMin(value = "0.5", message = "layoutScale must be at least 0.5")
    @DecimalMax(value = "1.35", message = "layoutScale must not exceed 1.35")
    private Double layoutScale;
}
