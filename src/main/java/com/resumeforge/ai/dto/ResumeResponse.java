package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeResponse {
    private Long id;
    private Long userId;
    private String title;
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
    // V20: section ordering / visibility / label config
    private String sectionsConfig;
    // COMPRESS FEATURE: density scale — see Resume.java for details.
    private Double layoutScale;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
