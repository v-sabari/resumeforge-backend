package com.resumeforge.ai.dto;

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
}
