package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TOOL-01: response for the free JD-Based ATS Score Checker + Missing Keywords
 * tool ({@code POST /api/public/tools/ats-score}).
 *
 * The {@code score} is an <b>estimated ATS compatibility score</b> (0-100)
 * computed server-side from the actual resume content and job description using
 * a documented, deterministic weighting (see
 * {@link com.resumeforge.ai.service.AtsKeywordAnalyzer}). It is NOT a
 * reproduction of any vendor's proprietary ATS algorithm and NOT a hiring
 * probability.
 *
 * Every keyword entry distinguishes an exact/normalized match from a
 * semantically-related match, and carries whether the job description treats
 * it as required vs preferred.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsScoreResponse {

    /** Estimated ATS compatibility score, 0-100. */
    private int score;

    /** Human-readable band for the score (e.g. "Strong match"). */
    private String scoreLabel;

    /** Letter-style grade derived from the score. */
    private String grade;

    private String fileName;

    private int resumeWordCount;

    private int jobWordCount;

    /** How many job-description terms the resume matched exactly/normalized. */
    private int matchedCount;

    /** How many JD terms were judged missing. */
    private int missingKeywordCount;

    /** Exact + normalized keyword matches. */
    private List<String> matchedKeywords;

    /** Job terms matched only through a semantically-related term. */
    private List<String> semanticMatches;

    /** Missing/weak keywords, prioritised by relevance. */
    private List<KeywordItem> missingKeywords;

    /** Important requirements found in the job description (required first). */
    private List<KeywordItem> jobRequirements;

    /** Relevant experience/qualification signals identified in the resume. */
    private List<String> experienceHighlights;

    /** Strengths observed in the resume relative to the job. */
    private List<String> strengths;

    /** Suggested resume improvements (actionable). */
    private List<String> improvements;

    /** Specific recommendations to improve alignment with the job description. */
    private List<String> recommendations;

    /** Per-factor breakdown that produced the final score. */
    private List<Factor> factorScores;

    /** True when the OpenRouter semantic enrichment step ran successfully. */
    private boolean aiEnhanced;

    private String disclaimer;

    // ── Nested types ──────────────────────────────────────────────────────────

    /** A job-description keyword and its match status/priority. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordItem {
        private String keyword;
        /** True when the JD clearly frames this as a must-have/required qualification. */
        private boolean required;
        /** Relative importance to the job description, higher = more prominent. */
        private int importance;
        /** EXACT | SEMANTIC | MISSING */
        private String matchType;
        /** The resume term that satisfied the requirement (exact or semantic). */
        private String matchedWith;
    }

    /** A single weighted scoring factor. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Factor {
        private String key;
        private String label;
        /** 0-100 factor value. */
        private int score;
        /** Contribution weight in percent (e.g. 40 = 40%). */
        private int weight;
    }
}
