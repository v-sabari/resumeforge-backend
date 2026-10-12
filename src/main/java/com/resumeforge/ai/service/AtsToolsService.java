package com.resumeforge.ai.service;

import tools.jackson.databind.JsonNode;
import com.resumeforge.ai.dto.AtsScoreResponse;
import com.resumeforge.ai.dto.FormatCheckResponse;
import com.resumeforge.ai.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TOOLS-01 / TOOLS-02: orchestration for the two free public tools that replace
 * the old mock "ATS Keyword Checker" and "Resume Word Count Checker" widgets.
 *
 * <ul>
 *   <li><b>TOOL-01</b> — JD-Based ATS Score Checker + Missing Keywords. The
 *       score is computed deterministically here from the parsed resume and the
 *       pasted job description. The optional AI pass only refines which missing
 *       job terms are semantically covered by the resume; it can never change
 *       the resume file, fabricate keywords, or set the score directly.</li>
 *   <li><b>TOOL-02</b> — ATS Format &amp; Parsing Checker. Fully deterministic
 *       ({@link FormatChecker}), no AI.</li>
 * </ul>
 *
 * <p>All inputs come from the actually uploaded file and the actually pasted
 * job description — there is no hardcoded or demo output anywhere.</p>
 */
@Service
public class AtsToolsService {

    private static final Logger log = LoggerFactory.getLogger(AtsToolsService.class);

    /** Minimum pasted job-description length (after trimming) to be useful. */
    private static final int MIN_JD_CHARS = 60;

    /** Keep the AI enrichment bounded — cost and prompt-size control. */
    private static final int MAX_TERMS_TO_AI = 25;
    private static final int MAX_RESUME_EXCERPT_FOR_AI = 4000;

    // Documented scoring weights (percent). Requirement coverage dominates
    // because ATS ranking is primarily driven by must-have coverage.
    private static final int W_REQUIRED = 40;
    private static final int W_KEYWORD = 20;
    private static final int W_PREFERRED = 10;
    private static final int W_EXPERIENCE = 15;
    private static final int W_EDUCATION = 5;
    private static final int W_FORMAT = 10;

    private final DocumentParsingService parsingService;
    private final AtsKeywordAnalyzer analyzer;
    private final FormatChecker formatChecker;
    private final AiService aiService;

    public AtsToolsService(DocumentParsingService parsingService,
                           AtsKeywordAnalyzer analyzer,
                           FormatChecker formatChecker,
                           AiService aiService) {
        this.parsingService = parsingService;
        this.analyzer = analyzer;
        this.formatChecker = formatChecker;
        this.aiService = aiService;
    }

    // ── TOOL-01 ───────────────────────────────────────────────────────────────

    public AtsScoreResponse atsScore(byte[] bytes, String filename, String contentType,
                                     String jobDescription) {
        if (jobDescription == null || jobDescription.trim().length() < MIN_JD_CHARS) {
            throw new BadRequestException(
                    "Please paste the full job description (at least " + MIN_JD_CHARS
                            + " characters) so the checker has something to compare against.");
        }
        String jd = jobDescription.trim();

        ParsedDocument doc = parseDocument(bytes, filename, contentType);
        if (!doc.hasUsableText()) {
            throw new BadRequestException(
                    "No readable text could be extracted from this file. Please upload a "
                            + "text-based PDF, DOCX or TXT resume (scans/images are not supported).");
        }

        AtsKeywordAnalyzer.Analysis a = analyzer.analyze(doc.text(), jd);
        if (a.all.isEmpty()) {
            throw new BadRequestException(
                    "No comparable skills or requirements were found in the job description. "
                            + "Please paste the full job posting.");
        }

        boolean aiEnhanced = false;
        if (aiService != null && !a.missing.isEmpty()) {
            try {
                Map<String, String> semantic = semanticMatches(a, doc.text());
                if (!semantic.isEmpty()) {
                    analyzer.applySemanticMatches(a, semantic);
                    aiEnhanced = true;
                }
            } catch (Exception ex) {
                // Non-fatal: the deterministic result stands on its own.
                log.warn("[AtsToolsService] Semantic enrichment skipped: {}", ex.getMessage());
            }
        }

        int formatQuality = formatChecker.readinessScore(doc);

        int score = clampScore((int) Math.round(
                a.requiredCoverage   * (W_REQUIRED / 100.0)
              + a.keywordCoverage    * (W_KEYWORD / 100.0)
              + a.preferredCoverage  * (W_PREFERRED / 100.0)
              + a.experienceAlignment * (W_EXPERIENCE / 100.0)
              + a.educationAlignment  * (W_EDUCATION / 100.0)
              + formatQuality         * (W_FORMAT / 100.0)));

        List<AtsScoreResponse.Factor> factors = List.of(
                factor("requiredCoverage", "Required keyword coverage", a.requiredCoverage, W_REQUIRED),
                factor("keywordCoverage", "Overall keyword coverage", a.keywordCoverage, W_KEYWORD),
                factor("preferredCoverage", "Preferred keyword coverage", a.preferredCoverage, W_PREFERRED),
                factor("experienceAlignment", "Experience alignment", a.experienceAlignment, W_EXPERIENCE),
                factor("educationAlignment", "Education alignment", a.educationAlignment, W_EDUCATION),
                factor("formatQuality", "Format & parseability", formatQuality, W_FORMAT));

        List<String> matchedKeywords = new ArrayList<>();
        List<String> semanticMatches = new ArrayList<>();
        for (AtsKeywordAnalyzer.Term t : a.matched) {
            if ("SEMANTIC".equals(t.matchType)) {
                semanticMatches.add(t.matchedWith == null || t.matchedWith.isBlank()
                        ? t.keyword
                        : t.keyword + " \u2248 " + t.matchedWith);
            } else {
                matchedKeywords.add(t.keyword);
            }
        }

        List<AtsScoreResponse.KeywordItem> missingKeywords = new ArrayList<>();
        for (AtsKeywordAnalyzer.Term t : a.missing) {
            missingKeywords.add(keywordItem(t));
        }

        List<AtsKeywordAnalyzer.Term> ordered = new ArrayList<>(a.all);
        ordered.sort((x, y) -> {
            if (x.required != y.required) return x.required ? -1 : 1;
            return Integer.compare(y.importance, x.importance);
        });
        List<AtsScoreResponse.KeywordItem> jobRequirements = new ArrayList<>();
        for (AtsKeywordAnalyzer.Term t : ordered) {
            jobRequirements.add(keywordItem(t));
        }

        List<String> strengths = new ArrayList<>();
        strengths.add("Matched " + a.matched.size() + " of " + a.all.size()
                + " job-description keywords (" + a.keywordCoverage + "%).");
        if (a.requiredMatched > 0) {
            strengths.add("Covers " + a.requiredMatched + " of " + a.requiredTotal
                    + " required keywords (" + a.requiredCoverage + "%).");
        }
        int shown = 0;
        for (AtsKeywordAnalyzer.Term t : a.matched) {
            if (t.required && shown++ < 5) strengths.add("Strong on required skill: " + t.keyword);
        }

        List<String> experienceHighlights = new ArrayList<>();
        if (a.experienceAlignment >= 70) {
            experienceHighlights.add("Experience level appears aligned with the role.");
        } else if (a.jobHasExperienceRequirement) {
            experienceHighlights.add("The job states a years-of-experience requirement that could "
                    + "not be fully verified from the resume text.");
        }
        if (a.jobHasEducationRequirement) {
            experienceHighlights.add("The job states an education requirement; the resume "
                    + (a.educationAlignment >= 70 ? "matches it." : "may not clearly match it."));
        }

        List<String> improvements = new ArrayList<>();
        int missingShown = 0;
        for (AtsKeywordAnalyzer.Term t : a.missing) {
            if (missingShown++ >= 10) break;
            improvements.add("Add evidence of \"" + t.keyword + "\" only if you genuinely have it — "
                    + "it appears as a " + (t.required ? "required" : "preferred")
                    + " skill you have not yet demonstrated.");
        }
        if (improvements.isEmpty()) {
            improvements.add("Strong keyword coverage — focus on quantifying your achievements.");
        }

        List<String> recommendations = new ArrayList<>();
        if (a.requiredCoverage < 60) {
            recommendations.add("Prioritise the required keywords you are missing; they carry the "
                    + "most weight in most ATS rankings.");
        }
        if (formatQuality < 70) {
            recommendations.add("Run the ATS Format & Parsing Checker — layout or parseability issues "
                    + "may be holding the resume back.");
        }
        if (a.preferredTotal > 0 && a.preferredCoverage < 50) {
            recommendations.add("Weave in the preferred (nice-to-have) keywords where truthful.");
        }
        recommendations.add("Use the exact terminology from the job description where it genuinely applies.");

        return AtsScoreResponse.builder()
                .score(score)
                .scoreLabel(scoreLabel(score))
                .grade(gradeFor(score))
                .fileName(filename)
                .resumeWordCount(doc.wordCount())
                .jobWordCount(countWords(jd))
                .matchedCount(a.matched.size())
                .missingKeywordCount(a.missing.size())
                .matchedKeywords(matchedKeywords)
                .semanticMatches(semanticMatches)
                .missingKeywords(missingKeywords)
                .jobRequirements(jobRequirements)
                .experienceHighlights(experienceHighlights)
                .strengths(strengths)
                .improvements(improvements)
                .recommendations(recommendations)
                .factorScores(factors)
                .aiEnhanced(aiEnhanced)
                .disclaimer("This is an estimated ATS compatibility score computed from your uploaded "
                        + "resume and the job description you pasted, using a documented weighting. It is "
                        + "NOT any specific vendor's proprietary ATS algorithm and NOT a prediction of "
                        + "hiring outcome.")
                .build();
    }

    // ── TOOL-02 ───────────────────────────────────────────────────────────────

    public FormatCheckResponse formatCheck(byte[] bytes, String filename, String contentType) {
        ParsedDocument doc = parseDocument(bytes, filename, contentType);
        return formatChecker.check(doc, filename);
    }

    // ── AI semantic enrichment ────────────────────────────────────────────────

    private Map<String, String> semanticMatches(AtsKeywordAnalyzer.Analysis a, String resumeText) {
        List<AtsKeywordAnalyzer.Term> missing = new ArrayList<>(a.missing);
        if (missing.isEmpty()) return Map.of();

        StringBuilder termList = new StringBuilder();
        int i = 0;
        for (AtsKeywordAnalyzer.Term t : missing) {
            if (i >= MAX_TERMS_TO_AI) break;
            termList.append(++i).append(". ").append(t.keyword).append('\n');
        }

        String excerpt = resumeText.length() > MAX_RESUME_EXCERPT_FOR_AI
                ? resumeText.substring(0, MAX_RESUME_EXCERPT_FOR_AI)
                : resumeText;

        String prompt = "You compare a resume against a list of job-description terms that were NOT "
                + "found verbatim. For each term, decide whether the resume ALREADY demonstrates the same "
                + "skill or technology using different words (e.g. \"Spring Boot\" vs \"Spring Framework\", "
                + "\"REST API\" vs \"RESTful web services\", \"Machine Learning\" vs \"ML\"). Report a term "
                + "ONLY when the resume genuinely covers it. Never guess and never invent.\n\n"
                + "MISSING TERMS:\n" + termList + "\n"
                + "RESUME TEXT (excerpt):\n" + excerpt + "\n\n"
                + "Respond with ONLY valid JSON matching exactly this schema, no other text:\n"
                + "{\"matches\":[{\"missing\":\"exact term from the list above\","
                + "\"resumeTerm\":\"the phrase in the resume that covers it\"}]}\n"
                + "If none are covered, return {\"matches\":[]}.";

        JsonNode response = aiService.publicSemanticMatch(prompt);
        if (response == null) return Map.of();

        Map<String, String> result = new LinkedHashMap<>();
        JsonNode matches = response.path("matches");
        if (matches.isArray()) {
            // Only accept terms that were actually in our missing list.
            Map<String, AtsKeywordAnalyzer.Term> byKey = new LinkedHashMap<>();
            for (AtsKeywordAnalyzer.Term t : missing) {
                byKey.put(AtsKeywordAnalyzer.normalize(t.keyword), t);
            }
            for (JsonNode m : matches) {
                String missingTerm = m.path("missing").asString("");
                String resumeTerm = m.path("resumeTerm").asString("");
                if (missingTerm.isBlank() || resumeTerm.isBlank()) continue;
                AtsKeywordAnalyzer.Term t = byKey.get(AtsKeywordAnalyzer.normalize(missingTerm));
                if (t != null) result.put(t.keyword, resumeTerm);
            }
        }
        return result;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ParsedDocument parseDocument(byte[] bytes, String filename, String contentType) {
        try {
            return parsingService.parse(bytes, filename, contentType);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(supportMessage(ex.getMessage()));
        }
    }

    private String supportMessage(String type) {
        if (type != null && type.startsWith("UNSUPPORTED:")) {
            String ext = type.substring("UNSUPPORTED:".length());
            return "The ." + ext + " format is not supported yet. Please upload a PDF, DOCX or TXT file.";
        }
        return (type == null || type.isBlank()) ? "The uploaded file could not be read." : type;
    }

    private AtsScoreResponse.Factor factor(String key, String label, int score, int weight) {
        return AtsScoreResponse.Factor.builder()
                .key(key).label(label).score(score).weight(weight).build();
    }

    private AtsScoreResponse.KeywordItem keywordItem(AtsKeywordAnalyzer.Term t) {
        return AtsScoreResponse.KeywordItem.builder()
                .keyword(t.keyword)
                .required(t.required)
                .importance(t.importance)
                .matchType(t.matchType)
                .matchedWith(t.matchedWith)
                .build();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private static int clampScore(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static String scoreLabel(int score) {
        if (score >= 85) return "Excellent match";
        if (score >= 70) return "Strong match";
        if (score >= 55) return "Moderate match";
        if (score >= 40) return "Weak match";
        return "Poor match";
    }

    private static String gradeFor(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }
}
