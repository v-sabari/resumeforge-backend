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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
