package com.cvcraft.ai.dto;
import java.util.List;
public record AiSummaryRequest(String targetRole, List<String> skills, List<String> achievements,
    List<String> highlights, String currentSummary) {}
