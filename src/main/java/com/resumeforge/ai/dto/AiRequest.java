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

    // AI-02 FIX: these three were sent by the frontend's /api/ai/skills
    // payload (currentSkills, experienceKeywords, projectKeywords) but had
    // no matching field here, so Jackson silently dropped them and
    // AiService fell back to the never-populated `content` field.
    private List<String> currentSkills;
    private List<String> experienceKeywords;
    private List<String> projectKeywords;

    // AI-02 FIX: sent by the frontend's /api/ai/bullets payload as
    // `currentText` — used to give the model the existing bullet text (if any)
    // as context for the rewrite.
    private String currentText;

    // BULLETS-01: dedicated fields for the improved Bullet Points feature.
    // The structured form collects the section type, what the user actually
    // did, and (optional) outcome + metrics. The AI is strictly forbidden from
    // inventing facts — it may only restate/improve what the user provided.
    private String sectionType;      // Work Experience / Internship / Project /
                                     // Achievement / Leadership / Other
    private String description;      // "What did you do?" — the user's own words
    private String outcome;          // "Result / Outcome" — optional
    private String metrics;          // "Metrics" — optional, never fabricated
    private Integer numBullets;      // Number of bullets to generate (1–5)
}