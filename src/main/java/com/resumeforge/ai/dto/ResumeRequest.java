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
            regexp = "^(classic|modern|minimal|professional|executive|fresher|creative)$",
            message = "Template must be one of: classic, modern, minimal, professional, executive, fresher, creative"
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
    @DecimalMin(value = "0.5", message = "layoutScale must be at least 0.5")
    @DecimalMax(value = "1.0", message = "layoutScale must not exceed 1.0")
    private Double layoutScale;
}
