package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.AiUsageLog;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.AiUsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    // ── Feature identifiers (stored in ai_usage_logs.feature) ────────
    public static final String FEATURE_SUMMARY       = "summary";
    public static final String FEATURE_BULLETS       = "bullets";
    public static final String FEATURE_SKILLS        = "skills";
    public static final String FEATURE_REWRITE       = "rewrite";
    public static final String FEATURE_ATS_SCORE     = "ats_score";
    public static final String FEATURE_COVER_LETTER  = "cover_letter";
    public static final String FEATURE_TAILOR        = "tailor";
    public static final String FEATURE_LINKEDIN      = "linkedin";
    public static final String FEATURE_INTERVIEW     = "interview_prep";
    public static final String FEATURE_GRAMMAR       = "grammar_check";

    // ── Daily limits for free users (premium = unlimited) ────────────
    private static final int FREE_ATS_SCORE_DAILY   = 3;
    private static final int FREE_LINKEDIN_DAILY     = 1;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AiUsageLogRepository usageLogRepository;

    @Value("${app.openrouter.api-key}")   private String apiKey;
    @Value("${app.openrouter.base-url}")  private String baseUrl;
    @Value("${app.openrouter.model}")     private String model;
    @Value("${app.openrouter.site-url}")  private String siteUrl;
    @Value("${app.openrouter.site-name}") private String siteName;

    public AiService(WebClient.Builder builder,
                     ObjectMapper objectMapper,
                     AiUsageLogRepository usageLogRepository) {
        this.webClient         = builder.build();
        this.objectMapper      = objectMapper;
        this.usageLogRepository = usageLogRepository;
    }

    // ═════════════════════════════════════════════════════════════════
    // ORIGINAL FEATURES (preserved, now with usage logging)
    // ═════════════════════════════════════════════════════════════════

    public AiTextResponse generateSummary(AiSummaryRequest request, User user) {
        String prompt = """
                Write a professional ATS-optimized resume summary.

                Candidate details:
                - Target role: %s
                - Skills: %s
                - Achievements: %s
                - Highlights: %s
                - Current summary: %s

                Instructions:
                - Write 2 to 4 concise sentences
                - Sound professional, modern, and credible
                - Be ATS-friendly and keyword-rich
                - Avoid fake metrics, fake companies, or invented claims
                - Return plain text only
                """.formatted(
                safe(request.targetRole()),
                joinList(request.skills()),
                joinList(request.achievements()),
                joinList(request.highlights()),
                safe(request.currentSummary())
        );
        AiCallResult result = callAiTracked(prompt, 220);
        logUsage(user, FEATURE_SUMMARY, result.tokensUsed());
        return new AiTextResponse(result.content());
    }

    public AiListResponse generateBullets(AiBulletsRequest request, User user) {
        String prompt = """
                Generate exactly 5 ATS-optimized resume bullet points.

                Candidate details:
                - Role: %s
                - Company: %s
                - Responsibilities: %s
                - Technologies: %s
                - Existing text: %s

                Instructions:
                - Use strong action verbs
                - Focus on outcomes, ownership, and measurable impact where reasonable
                - Do not invent unrealistic numbers
                - Each bullet must be one line
                - Return plain text with one bullet per line
                - Do not add headings or extra commentary
                """.formatted(
                safe(request.role()),
                safe(request.company()),
                joinList(request.responsibilities()),
                joinList(request.technologies()),
                safe(request.currentText())
        );
        AiCallResult result = callAiTracked(prompt, 320);
        logUsage(user, FEATURE_BULLETS, result.tokensUsed());

        List<String> bullets = result.content().lines()
                .map(String::trim)
                .map(this::stripBulletPrefix)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(5)
                .toList();

        return new AiListResponse(bullets);
    }

    public AiListResponse suggestSkills(AiSkillsRequest request, User user) {
        String prompt = """
                Suggest exactly 12 ATS-friendly resume skills for this candidate.

                Candidate details:
                - Target role: %s
                - Current skills: %s
                - Experience keywords: %s
                - Project keywords: %s

                Instructions:
                - Prioritize role-relevant hard skills first
                - Include important ATS keywords when appropriate
                - Avoid duplicates
                - Return comma-separated skills only
                - No numbering, no explanation
                """.formatted(
                safe(request.targetRole()),
                joinList(request.currentSkills()),
                joinList(request.experienceKeywords()),
                joinList(request.projectKeywords())
        );
        AiCallResult result = callAiTracked(prompt, 180);
        logUsage(user, FEATURE_SKILLS, result.tokensUsed());

        List<String> skills = Arrays.stream(result.content().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().limit(12).toList();

        return new AiListResponse(skills);
    }

    public AiTextResponse rewrite(AiRewriteRequest request, User user) {
        String prompt = """
                Rewrite the following resume text.

                Candidate details:
                - Target role: %s
                - Tone: %s
                - Original text: %s

                Instructions:
                - Improve clarity, professionalism, and ATS readability
                - Preserve the original meaning
                - Do not exaggerate or invent achievements
                - Keep it concise and recruiter-friendly
                - Return plain text only
                """.formatted(
                safe(request.targetRole()),
                safe(request.tone()),
                safe(request.text())
        );
        AiCallResult result = callAiTracked(prompt, 220);
        logUsage(user, FEATURE_REWRITE, result.tokensUsed());
        return new AiTextResponse(result.content());
    }

    // ═════════════════════════════════════════════════════════════════
    // NEW FEATURE: ATS SCORE  (free: 3/day | premium: unlimited)
    // ═════════════════════════════════════════════════════════════════

    public AtsScoreResponse analyzeAtsScore(AtsScoreRequest request, User user) {
        if (!user.isPremium()) {
            long usedToday = usageLogRepository.countUsageSince(
                    user, FEATURE_ATS_SCORE, Instant.now().minus(1, ChronoUnit.DAYS));
            if (usedToday >= FREE_ATS_SCORE_DAILY) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "Free users can run " + FREE_ATS_SCORE_DAILY +
                        " ATS scans per day. Upgrade to Premium for unlimited scans.");
            }
        }

        boolean hasJd = request.jobDescription() != null && !request.jobDescription().isBlank();

        String prompt = """
                Analyze this resume for ATS compatibility and return a JSON object only.

                Resume content:
                - Target role: %s
                - Summary: %s
                - Skills: %s
                - Experience bullets: %s
                - Achievements: %s
                %s

                Return ONLY this JSON structure, no markdown, no commentary:
                {
                  "score": <integer 0-100>,
                  "grade": "<Excellent|Good|Fair|Needs Work>",
                  "matchedKeywords": ["keyword1", "keyword2"],
                  "missingKeywords": ["keyword1", "keyword2"],
                  "topFixes": ["fix1", "fix2", "fix3"],
                  "summary": "<2 sentence assessment>"
                }

                Scoring guide:
                - 90-100: Excellent ATS pass rate
                - 75-89: Good, minor improvements possible
                - 60-74: Fair, several gaps
                - Below 60: Needs significant work

                topFixes: list max 5 fixes, ordered by impact, as complete sentences.
                matchedKeywords: keywords from the resume that match ATS or JD requirements.
                missingKeywords: important keywords missing from the resume.
                """.formatted(
                safe(request.targetRole()),
                safe(request.summary()),
                joinList(request.skills()),
                joinList(request.experienceBullets()),
                joinList(request.achievements()),
                hasJd ? "- Job description: " + request.jobDescription().substring(0,
                        Math.min(request.jobDescription().length(), 1500)) : ""
        );

        AiCallResult result = callAiTracked(prompt, 600);
        logUsage(user, FEATURE_ATS_SCORE, result.tokensUsed());
        return parseAtsScore(result.content());
    }

    // ═════════════════════════════════════════════════════════════════
    // NEW FEATURE: COVER LETTER  (premium only)
    // ═════════════════════════════════════════════════════════════════

    public AiTextResponse generateCoverLetter(CoverLetterRequest request, User user) {
        requirePremium(user, "Cover letter generation");

        String tone = (request.tone() == null || request.tone().isBlank())
                ? "professional" : request.tone().trim();

        String prompt = """
                Write a professional cover letter for a job application.

                Candidate details:
                - Name: %s
                - Target role: %s
                - Company: %s
                - Tone: %s
                - Key skills: %s
                - Top achievements: %s
                - Professional summary: %s
                %s

                Instructions:
                - Write exactly 3 paragraphs
                - Paragraph 1: Strong opening that names the role and expresses genuine interest
                - Paragraph 2: Highlight 2-3 specific, relevant achievements or skills
                - Paragraph 3: Confident closing with a call to action
                - Keep total length to 250-350 words
                - Sound human and specific, not generic or template-like
                - Do not use "I am writing to apply" as an opener
                - Do not invent facts, companies, or metrics not in the input
                - Return plain text only, no subject line, no headers
                """.formatted(
                safe(request.candidateName()),
                safe(request.targetRole()),
                safe(request.companyName()),
                tone,
                joinList(request.skills()),
                joinList(request.topAchievements()),
                safe(request.summary()),
                hasContent(request.jobDescription())
                        ? "- Job description keywords to target: " + request.jobDescription().substring(0,
                            Math.min(request.jobDescription().length(), 800))
                        : ""
        );

        AiCallResult result = callAiTracked(prompt, 550);
        logUsage(user, FEATURE_COVER_LETTER, result.tokensUsed());
        return new AiTextResponse(result.content());
    }

    // ═════════════════════════════════════════════════════════════════
    // NEW FEATURE: JOB-SPECIFIC TAILORING  (premium only)
    // ═════════════════════════════════════════════════════════════════

    public AiTailorResponse tailorResume(AiTailorRequest request, User user) {
        requirePremium(user, "Resume tailoring");

        if (!hasContent(request.jobDescription())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "jobDescription is required for resume tailoring.");
        }

        // Flatten bullet groups for the prompt (max 3 groups × 5 bullets)
        List<List<String>> groups = request.experienceBulletGroups() != null
                ? request.experienceBulletGroups().stream().limit(3).toList()
                : List.of();

        String bulletsSection = buildBulletGroupsPrompt(groups);

        String prompt = """
                Tailor this resume to match the job description. Return a JSON object only.

                Candidate:
                - Target role: %s
                - Current summary: %s
                - Skills: %s
                %s

                Job description (first 1200 chars):
                %s

                Return ONLY this JSON structure, no markdown, no commentary:
                {
                  "tailoredSummary": "<rewritten summary>",
                  "tailoredBulletGroups": [
                    ["bullet1", "bullet2", "bullet3"],
                    ["bullet1", "bullet2"]
                  ],
                  "suggestedSkillsToAdd": ["skill1", "skill2"],
                  "keywordsMissing": ["keyword1", "keyword2"]
                }

                Rules:
                - tailoredBulletGroups must have the same number of groups as the input
                - Rewrite bullets to use keywords from the JD naturally
                - Do not invent achievements, numbers, or companies
                - suggestedSkillsToAdd: skills from the JD the candidate should add (max 6)
                - keywordsMissing: important JD terms not in the resume (max 8)
                """.formatted(
                safe(request.targetRole()),
                safe(request.currentSummary()),
                joinList(request.skills()),
                bulletsSection,
                request.jobDescription().substring(0,
                        Math.min(request.jobDescription().length(), 1200))
        );

        AiCallResult result = callAiTracked(prompt, 900);
        logUsage(user, FEATURE_TAILOR, result.tokensUsed());
        return parseTailorResponse(result.content(), groups.size());
    }

    // ═════════════════════════════════════════════════════════════════
    // NEW FEATURE: LINKEDIN OPTIMIZER  (free: 1/day | premium: unlimited)
    // ═════════════════════════════════════════════════════════════════

    public LinkedInResponse optimizeLinkedIn(LinkedInRequest request, User user) {
        if (!user.isPremium()) {
            long usedToday = usageLogRepository.countUsageSince(
                    user, FEATURE_LINKEDIN, Instant.now().minus(1, ChronoUnit.DAYS));
            if (usedToday >= FREE_LINKEDIN_DAILY) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "Free users can optimize LinkedIn once per day. " +
                        "Upgrade to Premium for unlimited access.");
            }
        }

        String prompt = """
                Optimize this LinkedIn profile. Return a JSON object only.

                Candidate:
                - Current role: %s
                - Target role: %s
                - Current headline: %s
                - Current about: %s
                - Top skills: %s
                - Achievements: %s

                Return ONLY this JSON structure, no markdown, no commentary:
                {
                  "optimizedHeadline": "<headline max 220 chars>",
                  "optimizedAbout": "<about section 3-4 paragraphs>",
                  "headlineTips": "<1-2 sentences explaining the headline choices>"
                }

                Headline rules:
                - Max 220 characters
                - Lead with target role title
                - Include 2-3 high-value keywords recruiters search for
                - Mention a key strength or differentiator
                - No buzzwords like "passionate", "guru", "ninja", "rockstar"

                About section rules:
                - 3 to 4 paragraphs, 200-350 words total
                - First sentence is a strong hook (not "I am a...")
                - Highlight top 2-3 achievements with specifics
                - End with a call to action or open invitation to connect
                - Sound like a real person, not a job listing
                """.formatted(
                safe(request.currentRole()),
                safe(request.targetRole()),
                safe(request.currentHeadline()),
                safe(request.currentAbout()),
                joinList(request.topSkills()),
                joinList(request.achievements())
        );

        AiCallResult result = callAiTracked(prompt, 700);
        logUsage(user, FEATURE_LINKEDIN, result.tokensUsed());
        return parseLinkedInResponse(result.content());
    }

    // ═════════════════════════════════════════════════════════════════
    // NEW FEATURE: INTERVIEW PREP  (premium only)
    // ═════════════════════════════════════════════════════════════════

    public InterviewPrepResponse generateInterviewPrep(InterviewPrepRequest request, User user) {
        requirePremium(user, "Interview preparation");

        String prompt = """
                Generate 5 interview questions with model answers. Return a JSON object only.

                Candidate:
                - Target role: %s
                - Company: %s
                - Summary: %s
                - Key skills: %s
                - Top achievements: %s
                %s

                Return ONLY this JSON structure, no markdown, no commentary:
                {
                  "questions": [
                    {
                      "question": "<interview question>",
                      "modelAnswer": "<tailored answer using STAR method where appropriate>",
                      "category": "<behavioural|technical|situational|motivation>"
                    }
                  ],
                  "generalTips": "<2-3 sentences of role-specific interview advice>"
                }

                Rules:
                - Include a mix of categories: at least 1 behavioural, 1 technical, 1 motivation
                - Answers must reference the candidate's actual experience (from input) - do not invent
                - Answers should be 80-150 words each, concise and confident
                - Questions should be realistic for a %s interview
                - generalTips should be specific to this role and company if provided
                """.formatted(
                safe(request.targetRole()),
                safe(request.companyName()),
                safe(request.summary()),
                joinList(request.skills()),
                joinList(request.topAchievements()),
                hasContent(request.jobDescription())
                        ? "- Job description focus areas: " + request.jobDescription().substring(0,
                            Math.min(request.jobDescription().length(), 600))
                        : "",
                safe(request.targetRole())
        );

        AiCallResult result = callAiTracked(prompt, 1000);
        logUsage(user, FEATURE_INTERVIEW, result.tokensUsed());
        return parseInterviewPrepResponse(result.content());
    }

    // ═════════════════════════════════════════════════════════════════
    // NEW FEATURE: GRAMMAR & CLARITY CHECK  (all users, no daily cap)
    // ═════════════════════════════════════════════════════════════════

    public GrammarCheckResponse checkGrammar(GrammarCheckRequest request, User user) {
        if (!hasContent(request.text())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "text is required.");
        }
        String text = request.text().trim();
        if (text.length() > 3000) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Text exceeds 3000 character limit. Please check in smaller sections.");
        }

        String context = (request.context() == null || request.context().isBlank())
                ? "general" : request.context().trim();

        String prompt = """
                Check the following resume text for grammar, clarity, and ATS issues. Return JSON only.

                Context: %s
                Text to check:
                "%s"

                Return ONLY this JSON structure, no markdown, no commentary:
                {
                  "correctedText": "<full corrected version>",
                  "issuesFound": ["issue1", "issue2"],
                  "issueCount": <integer>,
                  "clean": <true|false>
                }

                Check for:
                - Grammar and spelling errors
                - Awkward phrasing that hurts clarity
                - Passive voice that weakens impact (flag but don't always fix)
                - Missing action verbs in bullets
                - ATS-unfriendly formatting (tables described, graphics described)
                - Tense inconsistencies

                issuesFound: describe each issue in plain English (max 10 issues)
                clean: true only if issueCount is 0
                correctedText: return the full text with corrections applied
                """.formatted(context, text);

        AiCallResult result = callAiTracked(prompt, 500);
        logUsage(user, FEATURE_GRAMMAR, result.tokensUsed());
        return parseGrammarResponse(result.content(), text);
    }

    // ═════════════════════════════════════════════════════════════════
    // CORE AI CALL  (tracked — returns content + token count)
    // ═════════════════════════════════════════════════════════════════

    private AiCallResult callAiTracked(String prompt, int maxTokens) {
        validateConfiguration();
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", """
                                    You are an expert resume writing assistant and career coach.
                                    Write professional, ATS-friendly, concise output.
                                    Never invent fake experience, fake employers, fake certifications, or fake achievements.
                                    When asked for JSON, return ONLY valid JSON — no markdown code fences, no commentary.
                                    """),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.7,
                    "max_tokens", maxTokens
            );

            String responseBody = webClient.post()
                    .uri(baseUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header("HTTP-Referer", siteUrl.trim())
                    .header("X-Title", siteName.trim())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class)
                                    .defaultIfEmpty("No error body")
                                    .map(body -> new RuntimeException(
                                            "OpenRouter API error: HTTP " + resp.statusCode().value() + " - " + body))
                    )
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw new RuntimeException("Empty response from OpenRouter");
            }

            JsonNode root     = objectMapper.readTree(responseBody);
            JsonNode choices  = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("OpenRouter response missing choices: " + responseBody);
            }

            String content = choices.get(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new RuntimeException("OpenRouter returned blank content");
            }

            // Extract token usage when available
            int tokensUsed = 0;
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                tokensUsed = usage.path("total_tokens").asInt(0);
            }

            return new AiCallResult(content, tokensUsed);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI generation failed", e);
            throw new RuntimeException("AI generation failed: " + e.getMessage(), e);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // JSON PARSERS  (all structured AI responses)
    // ═════════════════════════════════════════════════════════════════

    private AtsScoreResponse parseAtsScore(String raw) {
        try {
            JsonNode node = objectMapper.readTree(stripJsonFences(raw));
            return new AtsScoreResponse(
                    clampScore(node.path("score").asInt(50)),
                    gradeFromScore(node.path("score").asInt(50), node.path("grade").asText("")),
                    parseStringList(node.path("matchedKeywords")),
                    parseStringList(node.path("missingKeywords")),
                    parseStringList(node.path("topFixes")),
                    node.path("summary").asText("Analysis complete.")
            );
        } catch (Exception e) {
            log.error("Failed to parse ATS score response: {}", raw, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI response parsing failed. Please try again.");
        }
    }

    private AiTailorResponse parseTailorResponse(String raw, int expectedGroups) {
        try {
            JsonNode node = objectMapper.readTree(stripJsonFences(raw));
            String tailoredSummary = node.path("tailoredSummary").asText("");

            List<List<String>> bulletGroups = new ArrayList<>();
            JsonNode groupsNode = node.path("tailoredBulletGroups");
            if (groupsNode.isArray()) {
                for (JsonNode g : groupsNode) {
                    bulletGroups.add(parseStringList(g));
                }
            }
            // Pad with empty groups if AI returned fewer than expected
            while (bulletGroups.size() < expectedGroups) {
                bulletGroups.add(List.of());
            }

            return new AiTailorResponse(
                    tailoredSummary,
                    bulletGroups,
                    parseStringList(node.path("suggestedSkillsToAdd")),
                    parseStringList(node.path("keywordsMissing"))
            );
        } catch (Exception e) {
            log.error("Failed to parse tailor response: {}", raw, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI response parsing failed. Please try again.");
        }
    }

    private LinkedInResponse parseLinkedInResponse(String raw) {
        try {
            JsonNode node = objectMapper.readTree(stripJsonFences(raw));
            return new LinkedInResponse(
                    node.path("optimizedHeadline").asText(""),
                    node.path("optimizedAbout").asText(""),
                    node.path("headlineTips").asText("")
            );
        } catch (Exception e) {
            log.error("Failed to parse LinkedIn response: {}", raw, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI response parsing failed. Please try again.");
        }
    }

    private InterviewPrepResponse parseInterviewPrepResponse(String raw) {
        try {
            JsonNode node = objectMapper.readTree(stripJsonFences(raw));
            List<InterviewPrepResponse.QAPair> pairs = new ArrayList<>();
            JsonNode questionsNode = node.path("questions");
            if (questionsNode.isArray()) {
                for (JsonNode q : questionsNode) {
                    pairs.add(new InterviewPrepResponse.QAPair(
                            q.path("question").asText(""),
                            q.path("modelAnswer").asText(""),
                            q.path("category").asText("general")
                    ));
                }
            }
            return new InterviewPrepResponse(pairs, node.path("generalTips").asText(""));
        } catch (Exception e) {
            log.error("Failed to parse interview prep response: {}", raw, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI response parsing failed. Please try again.");
        }
    }

    private GrammarCheckResponse parseGrammarResponse(String raw, String originalText) {
        try {
            JsonNode node = objectMapper.readTree(stripJsonFences(raw));
            List<String> issues = parseStringList(node.path("issuesFound"));
            int count = node.path("issueCount").asInt(issues.size());
            return new GrammarCheckResponse(
                    node.path("correctedText").asText(originalText),
                    issues,
                    count,
                    count == 0
            );
        } catch (Exception e) {
            log.error("Failed to parse grammar response: {}", raw, e);
            // Graceful fallback — return original text rather than failing hard
            return new GrammarCheckResponse(originalText, List.of(
                    "Could not parse AI response. Please try again."), 1, false);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // USAGE LOGGING  (fire-and-forget — never blocks the response)
    // ═════════════════════════════════════════════════════════════════

    private void logUsage(User user, String feature, int tokensUsed) {
        try {
            AiUsageLog log = AiUsageLog.builder()
                    .user(user)
                    .feature(feature)
                    .tokensUsed(tokensUsed > 0 ? tokensUsed : null)
                    .wasPremium(user.isPremium())
                    .build();
            usageLogRepository.save(log);
        } catch (Exception e) {
            // Never let logging failure break the user's request
            AiService.log.warn("Failed to log AI usage for user={} feature={}: {}",
                    user.getId(), feature, e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════

    private void requirePremium(User user, String featureName) {
        if (!user.isPremium()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    featureName + " is a Premium feature. Upgrade to access it.");
        }
    }

    /** Strip ```json ... ``` or ``` ... ``` fences that the AI sometimes adds. */
    private String stripJsonFences(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) s = s.substring(firstNewline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String val = item.asText("").trim();
                if (!val.isBlank()) result.add(val);
            }
        }
        return result;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String gradeFromScore(int score, String aiGrade) {
        if (aiGrade != null && List.of("Excellent", "Good", "Fair", "Needs Work").contains(aiGrade)) {
            return aiGrade;
        }
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Good";
        if (score >= 60) return "Fair";
        return "Needs Work";
    }

    private String buildBulletGroupsPrompt(List<List<String>> groups) {
        if (groups.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("- Experience bullet groups:\n");
        for (int i = 0; i < groups.size(); i++) {
            sb.append("  Group ").append(i + 1).append(": ").append(joinList(groups.get(i))).append("\n");
        }
        return sb.toString();
    }

    private void validateConfiguration() {
        if (isBlank(apiKey))   throw new RuntimeException("OpenRouter API key is missing");
        if (isBlank(baseUrl))  throw new RuntimeException("OpenRouter base URL is missing");
        if (isBlank(model))    throw new RuntimeException("OpenRouter model is missing");
        if (isBlank(siteUrl))  throw new RuntimeException("OpenRouter site URL is missing");
        if (isBlank(siteName)) throw new RuntimeException("OpenRouter site name is missing");
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String safe(String v)        { return v == null ? "" : v.trim(); }
    private boolean isBlank(String v)    { return v == null || v.trim().isBlank(); }
    private boolean hasContent(String v) { return v != null && !v.trim().isBlank(); }

    private String stripBulletPrefix(String v) {
        return v.replaceFirst("^[-•*]\\s*", "").replaceFirst("^\\d+[.)]\\s*", "").trim();
    }

    /** Internal result carrier — keeps content and token count together. */
    private record AiCallResult(String content, int tokensUsed) {}
}
