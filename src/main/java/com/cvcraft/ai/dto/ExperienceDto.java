package com.cvcraft.ai.dto;
import java.util.List;
public record ExperienceDto(Long id, String company, String role, String location, String startDate, String endDate, List<String> bullets) {}
