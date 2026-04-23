package com.resumeforge.ai.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiRequest {
    private String content;
    private String context;
    private String jobDescription;

    private String targetRole;
    private List<String> skills;
    private List<String> achievements;
    private List<String> experienceBullets;
    private String currentSummary;
    private String currentRole;
    private String currentHeadline;
    private String currentAbout;
    private List<String> topSkills;
    private String companyName;
    private String candidateName;
    private String tone;
    private List<List<String>> experienceBulletGroups;
    private String summary;
    private List<String> topAchievements;
    private List<String> responsibilities;
    private List<String> technologies;
    private String role;
    private String company;
    private String text;
}