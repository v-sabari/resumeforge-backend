package com.resumeforge.ai.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ResumeDto(
        Long id,
        @Size(max = 180) String fullName,
        @Size(max = 180) String role,
        @Size(max = 180) String email,
        @Size(max = 60) String phone,
        @Size(max = 180) String location,
        @Size(max = 255) String linkedin,
        @Size(max = 255) String github,
        @Size(max = 255) String portfolio,
        String summary,
        List<String> skills,
        List<ExperienceDto> experiences,
        List<EducationDto> education,
        List<ProjectDto> projects,
        List<String> certifications,
        List<String> achievements,
        Instant createdAt,
        Instant updatedAt
) {
}
