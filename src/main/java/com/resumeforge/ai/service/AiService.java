package com.resumeforge.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.AiRequest;
import com.resumeforge.ai.entity.AiUsageLog;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.AiException;
import com.resumeforge.ai.exception.RateLimitException;
import com.resumeforge.ai.repository.AiUsageLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI-01 FIX — Production-hardened OpenRouter client.
 * (see original class-level javadoc history: retry template, typed timeouts,
 *  typed AiException mapping, key masking, null/blank response guards.)
 *
 * AI-02 FIX — Request/response contract repair.
 *
 * Two compounding bugs made every one of the 10 AI features non-functional:
 *
 * 1. REQUEST SIDE: every buildXxxPrompt() method read only
 *    {@code request.getContent()}. The frontend never sends a `content`
 *    field for any action — it sends feature-specific structured fields
 *    (targetRole, skills, currentSummary, jobDescription, text, ...) that
 *    mostly already existed on {@link AiRequest} but were simply never
 *    read. `content` was always null, so every prompt sent to OpenRouter
 *    ended in the literal string "null" (Java string concatenation of a
 *    null reference), which the model correctly refused to act on.
 *    FIX: every prompt builder now reads the actual fields the frontend
 *    sends (verified 1:1 against services/aiService.js + AIActionPanel.jsx).
 *
 * 2. RESPONSE SIDE: even with content populated, the old code asked the
 *    model for free text and returned {@code {result, inputTokens,
 *    outputTokens}}. The frontend expects a different, structured JSON
 *    shape per feature (e.g. {items:[...]}, {score, grade,
 *    matchedKeywords, missingKeywords, topFixes, summary},
 *    {correctedText, issuesFound, issueCount, clean}, ...) and several of
 *    those destructures (e.g. `issuesFound.length`) have no null guard,
 *    so a mismatched shape throws in the UI rather than just looking
 *    empty.
 *    FIX: every prompt now explicitly instructs the model to return ONLY
 *    JSON matching the exact schema the frontend expects, the OpenRouter
 *    call is made with `response_format: json_object`, and the raw model
 *    output is parsed into a {@link JsonNode} and returned to the client
 *    as-is (Spring/Jackson serializes JsonNode natively), instead of being
 *    wrapped in the old flat AiResponse DTO.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final int FREE_DAILY_LIMIT    = 5;
    private static final int PREMIUM_DAILY_LIMIT = 50;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Value("${app.openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${app.openrouter.base-url}")
    private String openRouterBaseUrl;

    @Value("${app.openrouter.model}")
    private String model;

    @Value("${app.openrouter.site-url}")
    private String siteUrl;

    @Value("${app.openrouter.site-name}")
    private String siteName;

    @Value("${app.openrouter.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${app.openrouter.read-timeout-ms:60000}")
    private int readTimeoutMs;

    private RestTemplate restTemplate;
    private RetryTemplate retryTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Startup ───────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
            throw new IllegalStateException(
                    "[AiService] OPENROUTER_API_KEY is not set or blank. " +
                            "All AI endpoints will return errors. " +
                            "Fix: Render dashboard → your service → Environment → " +
                            "Add environment variable: OPENROUTER_API_KEY = sk-or-v1-..."
            );
        }
        if (openRouterApiKey.length() < 20) {
            log.warn("[AiService] OPENROUTER_API_KEY is unusually short ({} chars, tail='…{}').",
                    openRouterApiKey.length(), maskKey(openRouterApiKey));
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        restTemplate = new RestTemplate(factory);

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        RetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true) {
            @Override
            public boolean canRetry(org.springframework.retry.RetryContext context) {
                Throwable lastThrowable = context.getLastThrowable();
                if (lastThrowable == null) return true;
                if (lastThrowable instanceof AiException ae) {
                    return ae.getErrorCode() == AiException.ErrorCode.OPENROUTER_UNAVAILABLE
                            && context.getRetryCount() < 2;
                }
                return false;
            }
        };

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(1_500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOff);
        retryTemplate.setThrowLastExceptionOnExhausted(true);

        log.info("[AiService] Initialised. model='{}', connectTimeout={}ms, " +
                        "readTimeout={}ms, keyTail='…{}'.",
                model, connectTimeoutMs, readTimeoutMs, maskKey(openRouterApiKey));
    }

    // ── Public entry points ───────────────────────────────────────────────────
    // AI-02 FIX: each now builds its prompt from the actual fields the
    // frontend sends for that feature (see AIActionPanel.jsx `run()`),
    // not from the never-populated `content` field.
    //
    // AI-03 FIX (synchronous conversion): these were previously
    // @Async("aiTaskExecutor") returning CompletableFuture<JsonNode>. That
    // meant every /api/ai/* call involved a *second* Servlet async dispatch
    // to deliver the response once the background thread finished — and
    // intermittent live testing showed genuine, correctly-authenticated
    // requests occasionally coming back 401 on exactly these endpoints,
    // and only these endpoints (every synchronous controller in this app —
    // ResumeController, PremiumController, ExportController, etc. — has been
    // 100% reliable across every test). That is the exact structural
    // fingerprint of Spring Security's authorization check not being
    // guaranteed to see a preserved SecurityContext on the second (async)
    // dispatch under a STATELESS session policy, where there is no
    // session-backed SecurityContextRepository to restore it from.
    //
    // Rather than depend on that internal plumbing behaving consistently —
    // which isn't something that can be verified with certainty without a
    // live, running instance to test against — these methods now run
    // synchronously on the original, already-authenticated request thread,
    // start to finish. There is no second dispatch, so there is nothing for
    // that race to act on. The tradeoff is that a servlet container thread
    // is held for the duration of the OpenRouter call (up to ~60s worst
    // case with retries) instead of being freed immediately; at this app's
    // scale that is a far smaller risk than intermittently logging
    // authenticated users out.

    public JsonNode rewriteContent(User user, AiRequest request) {
        return callOpenRouter(user, "rewrite", buildRewritePrompt(request));
    }

    public JsonNode improveBullets(User user, AiRequest request) {
        return callOpenRouter(user, "bullets", buildBulletPrompt(request));
    }

    public JsonNode generateSummary(User user, AiRequest request) {
        return callOpenRouter(user, "summary", buildSummaryPrompt(request));
    }

    public JsonNode extractSkills(User user, AiRequest request) {
        return callOpenRouter(user, "skills", buildSkillsPrompt(request));
    }

    public JsonNode tailorToJob(User user, AiRequest request) {
        return callOpenRouter(user, "tailor", buildTailorPrompt(request));
    }

    public JsonNode atsScore(User user, AiRequest request) {
        return callOpenRouter(user, "ats_score", buildAtsScorePrompt(request));
    }

    public JsonNode generateCoverLetter(User user, AiRequest request) {
        return callOpenRouter(user, "cover_letter", buildCoverLetterPrompt(request));
    }

    public JsonNode optimizeLinkedIn(User user, AiRequest request) {
        return callOpenRouter(user, "linkedin", buildLinkedInPrompt(request));
    }

    public JsonNode checkGrammar(User user, AiRequest request) {
        return callOpenRouter(user, "grammar_check", buildGrammarCheckPrompt(request));
    }

    public JsonNode generateInterviewPrep(User user, AiRequest request) {
        return callOpenRouter(user, "interview_prep", buildInterviewPrepPrompt(request));
    }

    // ── Core call (private, uses RetryTemplate programmatically) ─────────────

    private JsonNode callOpenRouter(User user, String feature, String prompt) {

        int limit = user.isPremium() ? PREMIUM_DAILY_LIMIT : FREE_DAILY_LIMIT;
        LocalDateTime windowStart = LocalDateTime.now().minusHours(24);
        long usageCount = aiUsageLogRepository
                .countByUserIdAndCreatedAtAfter(user.getId(), windowStart);

        if (usageCount >= limit) {
            String tier = user.isPremium() ? "Premium" : "Free";
            throw new RateLimitException(
                    tier + " plan limit reached (" + limit + " AI requests per day). " +
                            (user.isPremium() ? "Please try again tomorrow."
                                    : "Upgrade to Premium for higher limits.")
            );
        }

        return retryTemplate.execute(retryCtx -> {
            if (retryCtx.getRetryCount() > 0) {
                log.warn("[AiService] Retry attempt {} for feature='{}' after: {}",
                        retryCtx.getRetryCount(), feature,
                        retryCtx.getLastThrowable().getMessage());
            }
            return doHttpCall(user, feature, prompt);
        });
    }

    /**
     * Performs the actual HTTP request to OpenRouter and parses the response.
     *
     * AI-02 FIX: requests `response_format: json_object` and parses the
     * model's message content as JSON (rather than returning it as a raw
     * free-text string), since every prompt now instructs the model to
     * respond with a specific JSON schema matching what the frontend reads.
     */
    private JsonNode doHttpCall(User user, String feature, String prompt) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openRouterApiKey);
        headers.set("HTTP-Referer", siteUrl);
        headers.set("X-Title", siteName);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        // AI-02 FIX: force strict JSON output so the response can be parsed
        // and handed straight to the frontend in the shape it expects.
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

        log.debug("[AiService] → OpenRouter feature='{}' userId={} model='{}'",
                feature, user.getId(), model);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    openRouterBaseUrl, HttpMethod.POST, httpEntity, String.class);

        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("[AiService] OpenRouter 401 Unauthorized. " +
                            "keyTail='…{}'. Check OPENROUTER_API_KEY on Render.",
                    maskKey(openRouterApiKey));
            throw new AiException(AiException.ErrorCode.OPENROUTER_AUTH_ERROR,
                    "OpenRouter authentication failed. The API key is invalid or revoked. " +
                            "Check OPENROUTER_API_KEY in Render Environment Variables.", e);

        } catch (HttpClientErrorException.Forbidden e) {
            log.error("[AiService] OpenRouter 403 Forbidden for feature='{}'. " +
                    "Model '{}' may require a different plan.", feature, model);
            throw new AiException(AiException.ErrorCode.OPENROUTER_FORBIDDEN,
                    "Access to the AI model was denied. The model '" + model +
                            "' may require a paid OpenRouter subscription.", e);

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("[AiService] OpenRouter upstream 429 for feature='{}'.", feature);
            throw new AiException(AiException.ErrorCode.OPENROUTER_RATE_LIMIT,
                    "The AI provider is temporarily rate-limited. Please wait and try again.", e);

        } catch (HttpClientErrorException e) {
            log.error("[AiService] OpenRouter {} for feature='{}': {}",
                    e.getStatusCode(), feature, e.getResponseBodyAsString());
            throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                    "AI request rejected (HTTP " + e.getStatusCode().value() +
                            "). Check your request content.", e);

        } catch (HttpServerErrorException e) {
            log.warn("[AiService] OpenRouter {} for feature='{}'. Retrying...",
                    e.getStatusCode(), feature);
            throw new AiException(AiException.ErrorCode.OPENROUTER_UNAVAILABLE,
                    "The AI provider is temporarily unavailable (HTTP " +
                            e.getStatusCode().value() + "). Please try again shortly.", e);

        } catch (ResourceAccessException e) {
            log.warn("[AiService] Network error reaching OpenRouter for feature='{}': {}",
                    feature, e.getMessage());
            throw new AiException(AiException.ErrorCode.OPENROUTER_UNAVAILABLE,
                    "Could not reach the AI service (network error). Please try again.", e);

        } catch (Exception e) {
            log.error("[AiService] Unexpected error for feature='{}': {}", feature, e.getMessage(), e);
            throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                    "An unexpected error occurred while calling the AI service.", e);
        }

        try {
            String responseBody = response.getBody();

            if (responseBody == null || responseBody.isBlank()) {
                log.error("[AiService] OpenRouter returned empty body for feature='{}'.", feature);
                throw new AiException(AiException.ErrorCode.OPENROUTER_EMPTY_RESPONSE,
                        "The AI service returned an empty response. Please try again.");
            }

            JsonNode envelope = objectMapper.readTree(responseBody);
            String   rawContent = envelope.at("/choices/0/message/content").asString(null);

            if (rawContent == null || rawContent.isBlank()) {
                log.error("[AiService] Empty content in OpenRouter response for feature='{}'. " +
                        "Body: {}", feature, responseBody);
                throw new AiException(AiException.ErrorCode.OPENROUTER_EMPTY_RESPONSE,
                        "The AI service returned no content. Please try again.");
            }

            int inputTokens  = envelope.at("/usage/prompt_tokens").asInt(0);
            int outputTokens = envelope.at("/usage/completion_tokens").asInt(0);

            log.debug("[AiService] ← OpenRouter OK feature='{}' userId={} in={} out={}",
                    feature, user.getId(), inputTokens, outputTokens);

            aiUsageLogRepository.save(AiUsageLog.builder()
                    .userId(user.getId())
                    .feature(feature)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .build());

            // AI-02 FIX: rawContent is itself a JSON string (we asked the
            // model for response_format=json_object) — parse it and hand
            // the structured object straight back to the controller.
            try {
                return objectMapper.readTree(rawContent);
            } catch (Exception parseEx) {
                log.error("[AiService] Model returned non-JSON content for feature='{}': {}",
                        feature, rawContent, parseEx);
                throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                        "The AI returned an unexpected format. Please try again.", parseEx);
            }

        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AiService] Failed to parse OpenRouter response for feature='{}': {}",
                    feature, e.getMessage(), e);
            throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                    "Failed to parse the AI response. Please try again.", e);
        }
    }

    // ── Key masking ───────────────────────────────────────────────────────────

    private String maskKey(String key) {
        if (key == null || key.length() < 4) return "****";
        return key.substring(key.length() - 4);
    }

    // ── Small formatting helpers ────────────────────────────────────────────

    private static String orEmpty(String s) {
        return (s == null || s.isBlank()) ? "(not provided)" : s;
    }

    private static String joinOrNone(List<String> items) {
        if (items == null || items.isEmpty()) return "(none provided)";
        return String.join(", ", items);
    }

    private static String joinGroupsOrNone(List<List<String>> groups) {
        if (groups == null || groups.isEmpty()) return "(none provided)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            sb.append("Role ").append(i + 1).append(": ")
              .append(joinOrNone(groups.get(i))).append("\n");
        }
        return sb.toString();
    }

    // ── Prompt builders ───────────────────────────────────────────────────────
    // AI-02 FIX: every builder now (a) reads the fields the frontend
    // actually sends for that action, and (b) ends with an explicit
    // "respond with ONLY this JSON schema" instruction so the model's
    // output can be parsed straight into the shape AIActionPanel.jsx expects.

    private String buildRewritePrompt(AiRequest r) {
        return "Rewrite the following resume text to be more professional, impactful, and " +
                "ATS-friendly for a \"" + orEmpty(r.getTargetRole()) + "\" role, in a " +
                orEmpty(r.getTone()) + " tone. Use strong action verbs and quantify " +
                "achievements where possible.\n\n" +
                "Original text:\n" + orEmpty(r.getText()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"text\": \"the rewritten text\"}";
    }

    private String buildBulletPrompt(AiRequest r) {
        return "Write 5 strong, ATS-friendly resume bullet points for the role \"" +
                orEmpty(r.getRole()) + "\" at \"" + orEmpty(r.getCompany()) + "\". " +
                "Start each with a strong action verb and quantify results where possible.\n\n" +
                "Responsibilities to draw on: " + joinOrNone(r.getResponsibilities()) + "\n" +
                "Relevant technologies: " + joinOrNone(r.getTechnologies()) + "\n" +
                "Existing bullet text (may be empty): " + orEmpty(r.getCurrentText()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"items\": [\"bullet 1\", \"bullet 2\", \"bullet 3\", \"bullet 4\", \"bullet 5\"]}";
    }

    private String buildSummaryPrompt(AiRequest r) {
        return "Create a professional resume summary (2-4 sentences) for a \"" +
                orEmpty(r.getTargetRole()) + "\" role. Make it compelling and ATS-friendly.\n\n" +
                "Key skills: " + joinOrNone(r.getSkills()) + "\n" +
                "Key achievements: " + joinOrNone(r.getAchievements()) + "\n" +
                "Current summary draft (may be empty): " + orEmpty(r.getCurrentSummary()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"text\": \"the generated summary\"}";
    }

    private String buildSkillsPrompt(AiRequest r) {
        return "Suggest a list of 10-15 relevant technical and soft skills (keywords) for a \"" +
                orEmpty(r.getTargetRole()) + "\" role, suitable for a resume and ATS matching.\n\n" +
                "Skills already listed (avoid pure duplicates): " + joinOrNone(r.getCurrentSkills()) + "\n" +
                "Keywords from experience: " + joinOrNone(r.getExperienceKeywords()) + "\n" +
                "Keywords from projects: " + joinOrNone(r.getProjectKeywords()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"items\": [\"Skill 1\", \"Skill 2\", \"...\"]}";
    }

    private String buildTailorPrompt(AiRequest r) {
        return "Tailor this resume content to match the following job description. " +
                "Emphasize relevant skills and experience while maintaining honesty — " +
                "do not invent experience that wasn't provided.\n\n" +
                "Target role: " + orEmpty(r.getTargetRole()) + "\n" +
                "Job description:\n" + orEmpty(r.getJobDescription()) + "\n\n" +
                "Current summary: " + orEmpty(r.getCurrentSummary()) + "\n" +
                "Current skills: " + joinOrNone(r.getSkills()) + "\n" +
                "Current experience bullets by role:\n" + joinGroupsOrNone(r.getExperienceBulletGroups()) + "\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"tailoredSummary\": \"...\", " +
                "\"tailoredBulletGroups\": [[\"bullet\", \"bullet\"], [\"bullet\"]], " +
                "\"suggestedSkillsToAdd\": [\"skill\"], " +
                "\"keywordsMissing\": [\"keyword\"]}\n" +
                "(tailoredBulletGroups must have the same number of groups, in the same order, " +
                "as \"Current experience bullets by role\" above.)";
    }

    private String buildAtsScorePrompt(AiRequest r) {
        return "Analyze this resume content for ATS (applicant tracking system) compatibility " +
                "and, if a job description is given, job match. Provide a score from 0-100.\n\n" +
                "Target role: " + orEmpty(r.getTargetRole()) + "\n" +
                "Job description: " + orEmpty(r.getJobDescription()) + "\n\n" +
                "Summary: " + orEmpty(r.getSummary()) + "\n" +
                "Skills: " + joinOrNone(r.getSkills()) + "\n" +
                "Experience bullets: " + joinOrNone(r.getExperienceBullets()) + "\n" +
                "Achievements: " + joinOrNone(r.getAchievements()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"score\": 0, \"grade\": \"A|B|C|D|F\", " +
                "\"matchedKeywords\": [\"keyword\"], \"missingKeywords\": [\"keyword\"], " +
                "\"topFixes\": [\"specific actionable fix\"], \"summary\": \"1-2 sentence overview\"}";
    }

    private String buildCoverLetterPrompt(AiRequest r) {
        return "Write a professional cover letter based on this candidate's resume details " +
                "and the job description. Make it personalized, enthusiastic, and highlight " +
                "relevant qualifications. 3-4 short paragraphs.\n\n" +
                "Candidate name: " + orEmpty(r.getCandidateName()) + "\n" +
                "Target role: " + orEmpty(r.getTargetRole()) + "\n" +
                "Company: " + orEmpty(r.getCompanyName()) + "\n" +
                "Tone: " + orEmpty(r.getTone()) + "\n" +
                "Job description: " + orEmpty(r.getJobDescription()) + "\n\n" +
                "Summary: " + orEmpty(r.getSummary()) + "\n" +
                "Top achievements: " + joinOrNone(r.getTopAchievements()) + "\n" +
                "Key skills: " + joinOrNone(r.getSkills()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"text\": \"the full cover letter\"}";
    }

    private String buildLinkedInPrompt(AiRequest r) {
        return "Optimize this candidate's LinkedIn presence. Create a compelling headline " +
                "and About section.\n\n" +
                "Current role: " + orEmpty(r.getCurrentRole()) + "\n" +
                "Target role: " + orEmpty(r.getTargetRole()) + "\n" +
                "Current headline: " + orEmpty(r.getCurrentHeadline()) + "\n" +
                "Current About section: " + orEmpty(r.getCurrentAbout()) + "\n" +
                "Top skills: " + joinOrNone(r.getTopSkills()) + "\n" +
                "Achievements: " + joinOrNone(r.getAchievements()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"optimizedHeadline\": \"...\", \"optimizedAbout\": \"...\", " +
                "\"headlineTips\": \"short tip on why this headline works\"}";
    }

    private String buildGrammarCheckPrompt(AiRequest r) {
        return "Check this text for grammar, spelling, and clarity issues. " +
                "Context: " + orEmpty(r.getContext()) + "\n\n" +
                "Text:\n" + orEmpty(r.getText()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"correctedText\": \"the corrected text (same as input if already clean)\", " +
                "\"issuesFound\": [\"description of issue 1\", \"description of issue 2\"], " +
                "\"issueCount\": 0, \"clean\": true}\n" +
                "(\"clean\" must be true and \"issuesFound\" an empty array if there are no issues; " +
                "\"issueCount\" must equal issuesFound.length.)";
    }

    private String buildInterviewPrepPrompt(AiRequest r) {
        return "Based on this candidate's background and job description, generate 5 likely " +
                "interview questions with strong model answers.\n\n" +
                "Target role: " + orEmpty(r.getTargetRole()) + "\n" +
                "Company: " + orEmpty(r.getCompanyName()) + "\n" +
                "Job description: " + orEmpty(r.getJobDescription()) + "\n\n" +
                "Summary: " + orEmpty(r.getSummary()) + "\n" +
                "Skills: " + joinOrNone(r.getSkills()) + "\n" +
                "Top achievements: " + joinOrNone(r.getTopAchievements()) + "\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"questions\": [{\"question\": \"...\", \"modelAnswer\": \"...\", " +
                "\"category\": \"Behavioral|Technical|Situational\"}], " +
                "\"generalTips\": \"1-2 sentences of general interview advice\"}\n" +
                "(Provide exactly 5 questions.)";
    }
}
