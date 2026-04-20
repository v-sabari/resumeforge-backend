package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
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
}
