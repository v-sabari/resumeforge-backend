package com.cvcraft.ai.dto;
import java.time.LocalDateTime;
import java.util.List;
public record ResumeDto(
    Long id,
    String fullName, String role, String email, String phone,
    String location, String linkedin, String github, String portfolio,
    String summary,
    List<String> skills,
    List<ExperienceDto> experiences,
    List<EducationDto> education,
    List<ProjectDto> projects,
    List<String> certifications,
    List<String> achievements,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
