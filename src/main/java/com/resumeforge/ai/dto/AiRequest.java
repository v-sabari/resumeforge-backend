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

    // SKILLS-02: dedicated fields for the improved Suggest Skills feature.
    // These are additive — the existing skills endpoint is reused and the
    // existing currentSkills / targetRole / jobDescription fields are kept.
    private String resumeInformation; // Resume content / sections the user pastes
    private String skillCategory;     // Technical Skills / Programming Languages /
                                      // Frameworks / Databases / Tools / Cloud / Soft / All

    // REWRITE-01: dedicated fields for the improved Rewrite Text feature.
    // The form collects the resume section being rewritten and the rewrite
    // style the user wants. The AI may only restate/improve the provided text —
    // it must never invent metrics, skills, or experience.
    private String resumeSection;     // Summary / Experience / Project / Education / Skills / Other
    private String rewriteStyle;      // Professional / Concise / ATS-Friendly / Stronger wording

    // ATS-01: complete resume content for real ATS scoring. The frontend
    // composes the full resume (all sections) into one text blob so the AI can
    // evaluate keyword/skill/experience/education alignment against the job
    // description. The AI returns individual factor scores (0-100); the
    // backend computes the weighted final score.
    private String resumeText;

    // GRAMMAR-01: dedicated field for the improved Grammar Check feature. The
    // form collects the text to check and the resume section it belongs to so
    // the AI can apply section-appropriate professional styling while only
    // correcting, never inventing, content.
    private String grammarSection;    // Summary / Experience / Project / Education / Skills / Other

    // LINKEDIN-01: dedicated fields for the improved LinkedIn Optimization feature.
    // The form collects full resume/profile information, the target job role,
    // and (optional) existing LinkedIn content so the AI can generate recruiter-
    // friendly LinkedIn content based only on what the user actually has.
    private String linkedinResumeInfo;    // Full resume/profile details (education, skills, experience, projects, achievements)
    private String linkedinExistingContent; // Optional: existing LinkedIn headline/about content

    // COVER-01: dedicated field for the improved Cover Letter feature. The form
    // collects full resume/profile information so the AI can generate a cover
    // letter based only on what the user actually has — never inventing
    // experience, achievements, skills, or metrics.
    private String coverResumeInfo; // Full resume details (education, skills, experience, projects)
    private String additionalInfo;  // Optional: anything else the user wants mentioned

    // TAILOR-01: dedicated field for the improved Tailor Resume feature. The
    // form collects the complete resume content so the AI can analyze it against
    // the job description and suggest improvements — never inventing skills,
    // experience, achievements, or metrics that are not already present.
    private String tailorResumeInfo; // Complete resume content for tailoring

    // INTERVIEW-01: dedicated fields for the improved Interview Preparation
    // feature. The form collects the full resume, target role, job description,
    // interview type, experience level, and desired number of questions so the
    // AI generates personalized questions based on the user's actual background —
    // never inventing experience, projects, or skills.
    private String interviewResumeInfo; // Full resume content
    private String interviewType;       // Technical / HR / Behavioral / Mixed
    private String experienceLevel;     // Fresher / Internship / Experienced
    private Integer questionCount;      // Number of questions (e.g. 10, 20, 30)
}