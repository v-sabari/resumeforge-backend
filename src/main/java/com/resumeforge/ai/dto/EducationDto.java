package com.resumeforge.ai.dto;

public record EducationDto(
        Long id,
        String institution,
        String degree,
        String field,
        String startDate,
        String endDate
) {
}
