package com.resumeforge.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.resumeforge.ai.dto.AiRequest;
import com.resumeforge.ai.entity.AiUsageLog;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.AiException;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.exception.RateLimitException;
import com.resumeforge.ai.exception.UnauthorizedException;
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

    // CHAT-01: cost-control / anti-abuse limits for the conversational builder.
    // Conversational LLM turns are more expensive than single-shot prompts, so
    // we cap session length, per-turn input/output length, and the daily chat
    // budget. These are generous for a real Premium user but prevent runaway spend.
    private static final int MAX_CHAT_MESSAGES        = 60;   // whole conversation (user+assistant)
    private static final int MAX_CHAT_INPUT_CHARS     = 3000; // per-turn user message
    private static final int MAX_CHAT_CONTEXT_CHARS   = 9000; // resume context blob
    private static final int MAX_CHAT_OUTPUT_CHARS    = 2000; // model response ceiling (prompt-instructed)
    private static final int CHAT_DAILY_LIMIT         = 150;  // premium-only daily chat turns

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
        // ATS-01: the AI returns the six factor scores (0-100) plus keyword and
        // advisory data. The AI is NOT allowed to decide the overall score — the
        // backend computes the weighted final score from the factors below.
        JsonNode analysis = callOpenRouter(user, "ats_score", buildAtsScorePrompt(request));

        if (analysis instanceof ObjectNode node) {
            int keyword  = clampScore(node.path("keywordMatch").asInt(0));
            int skills   = clampScore(node.path("skillsMatch").asInt(0));
            int exp      = clampScore(node.path("experienceRelevance").asInt(0));
            int edu      = clampScore(node.path("educationMatch").asInt(0));
            int struct   = clampScore(node.path("structureReadability").asInt(0));
            int align    = clampScore(node.path("jobAlignment").asInt(0));

            int score = clampScore((int) Math.round(
                      keyword * 0.30
                    + skills  * 0.25
                    + exp     * 0.15
                    + edu     * 0.10
                    + struct  * 0.10
                    + align   * 0.10
            ));

            node.put("score", score);
            node.put("grade", gradeFor(score));

            ObjectNode factors = node.putObject("factorScores");
            factors.put("keywordMatch", keyword);
            factors.put("skillsMatch", skills);
            factors.put("experienceRelevance", exp);
            factors.put("educationMatch", edu);
            factors.put("structureReadability", struct);
            factors.put("jobAlignment", align);
        }

        return analysis;
    }

    // ATS-01: clamp a factor/final score into the valid 0-100 range.
    private static int clampScore(int v) {
        return Math.max(0, Math.min(100, v));
    }

    // ATS-01: letter grade derived from the final calculated score.
    private static String gradeFor(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
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

    /**
     * CHAT-01: Premium-only conversational resume builder — single turn.
     *
     * The client sends the full in-memory conversation history each turn and we
     * forward it (role/content) to OpenRouter so the model can keep context and
     * avoid re-asking questions. This is path-gated to Premium users only:
     * {@link #requirePremium(User)} hard-blocks Free users with a 403 before any
     * AI/OpenRouter spend occurs.
     */
    public JsonNode chatWithAI(User user, AiRequest request) {
        requirePremium(user);
        List<AiRequest.ChatMessage> history = request.getChatHistory();
        if (history == null || history.isEmpty()) {
            throw new BadRequestException("A user message is required to continue the conversation.");
        }
        if (history.size() > MAX_CHAT_MESSAGES) {
            throw new BadRequestException(
                    "Conversation too long. Maximum " + MAX_CHAT_MESSAGES +
                            " messages per session. Please start a new conversation or clear it.");
        }
        return callOpenRouterChat(user, "chat_builder",
                buildChatMessages(request), buildChatPrompt(request));
    }

    /**
     * CHAT-01: Premium-only resume generation from the conversation context.
     * Converts the resume info collected during the chat into structured JSON
     * (summary, skills, experience, projects, education, certifications) using
     * ONLY what the user actually provided.
     */
    public JsonNode generateResumeFromChat(User user, AiRequest request) {
        requirePremium(user);
        if (!hasText(request.getChatResumeContext())) {
            throw new BadRequestException(
                    "No resume information collected yet. Chat with the AI first and then generate your resume.");
        }
        return callOpenRouter(user, "chat_resume_generate",
                buildChatGeneratePrompt(request));
    }

    private void requirePremium(User user) {
        if (user == null || !user.isPremium()) {
            throw new UnauthorizedException(
                    "The AI Resume Builder is a Premium feature. Please upgrade to Premium to build your resume through AI conversation.");
        }
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
     * CHAT-01: multi-turn variant of {@link #callOpenRouter}. Sends the whole
     * conversation history (system + user/assistant turns) to OpenRouter as a
     * proper messages array so the model retains context across turns and does
     * not re-ask questions. Reuses the SAME OpenRouter client/config as every
     * other feature — no second integration. Enforces the premium-only chat
     * daily budget and the per-turn input/output length limits.
     */
    private JsonNode callOpenRouterChat(User user, String feature,
                                         List<Map<String, Object>> messages, String systemPrompt) {

        long usageCount = aiUsageLogRepository
                .countByUserIdAndFeatureAndCreatedAtAfter(user.getId(), feature,
                        LocalDateTime.now().minusHours(24));
        if (usageCount >= (long) CHAT_DAILY_LIMIT) {
            throw new RateLimitException(
                    "Premium chat limit reached (" + CHAT_DAILY_LIMIT +
                            " chat messages per day). Please try again tomorrow.");
        }

        return retryTemplate.execute(retryCtx -> {
            if (retryCtx.getRetryCount() > 0) {
                log.warn("[AiService] Retry attempt {} for feature='{}' after: {}",
                        retryCtx.getRetryCount(), feature,
                        retryCtx.getLastThrowable().getMessage());
            }
            return doHttpCallWithMessages(user, feature, messages, systemPrompt);
        });
    }

    /**
     * Builds the ordered OpenRouter messages array for a chat turn: a system
     * message with the no-hallucination rules + context, then the conversation
     * history (or a single user message if the history was not sent).
     */
    private List<Map<String, Object>> buildChatMessages(AiRequest r) {
        List<Map<String, Object>> messages = new ArrayList<>();
        List<AiRequest.ChatMessage> history = r.getChatHistory();
        if (history != null) {
            for (AiRequest.ChatMessage m : history) {
                if (m.getContent() == null || m.getContent().isBlank()) continue;
                String role = ("assistant".equalsIgnoreCase(m.getRole()))
                        ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", m.getContent()));
            }
        }
        if (messages.isEmpty()) {
            messages.add(Map.of("role", "user",
                    "content", orEmpty(history != null && !history.isEmpty()
                            ? history.get(history.size() - 1).getContent()
                            : null)));
        }
        return messages;
    }

    /**
     * Performs the chat HTTP request to OpenRouter with a system message + message
     * history. Otherwise mirrors {@link #doHttpCall} (same headers, auth, retry
     * mapping, JSON parsing, usage logging).
     */
    private JsonNode doHttpCallWithMessages(User user, String feature,
                                            List<Map<String, Object>> history, String systemPrompt) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openRouterApiKey);
        headers.set("HTTP-Referer", siteUrl);
        headers.set("X-Title", siteName);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.addAll(history);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_tokens", 1024); // CHAT-01 output ceiling

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

        log.debug("[AiService] → OpenRouter chat feature='{}' userId={} messages={}",
                feature, user.getId(), messages.size());

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    openRouterBaseUrl, HttpMethod.POST, httpEntity, String.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("[AiService] OpenRouter 401 Unauthorized (chat). keyTail='…{}'.",
                    maskKey(openRouterApiKey));
            throw new AiException(AiException.ErrorCode.OPENROUTER_AUTH_ERROR,
                    "OpenRouter authentication failed. The API key is invalid or revoked.", e);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new AiException(AiException.ErrorCode.OPENROUTER_FORBIDDEN,
                    "Access to the AI model was denied. The model '" + model +
                            "' may require a paid OpenRouter subscription.", e);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AiException(AiException.ErrorCode.OPENROUTER_RATE_LIMIT,
                    "The AI provider is temporarily rate-limited. Please wait and try again.", e);
        } catch (HttpClientErrorException e) {
            throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                    "AI request rejected (HTTP " + e.getStatusCode().value() +
                            "). Check your request content.", e);
        } catch (HttpServerErrorException e) {
            log.warn("[AiService] OpenRouter {} for chat feature='{}'. Retrying...",
                    e.getStatusCode(), feature);
            throw new AiException(AiException.ErrorCode.OPENROUTER_UNAVAILABLE,
                    "The AI provider is temporarily unavailable. Please try again shortly.", e);
        } catch (ResourceAccessException e) {
            throw new AiException(AiException.ErrorCode.OPENROUTER_UNAVAILABLE,
                    "Could not reach the AI service (network error). Please try again.", e);
        } catch (Exception e) {
            throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                    "An unexpected error occurred while calling the AI service.", e);
        }

        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new AiException(AiException.ErrorCode.OPENROUTER_EMPTY_RESPONSE,
                    "The AI service returned an empty response. Please try again.");
        }

        try {
            JsonNode envelope = objectMapper.readTree(responseBody);
            String rawContent = envelope.at("/choices/0/message/content").asString(null);
            if (rawContent == null || rawContent.isBlank()) {
                throw new AiException(AiException.ErrorCode.OPENROUTER_EMPTY_RESPONSE,
                        "The AI service returned no content. Please try again.");
            }
            int inputTokens  = envelope.at("/usage/prompt_tokens").asInt(0);
            int outputTokens = envelope.at("/usage/completion_tokens").asInt(0);
            log.debug("[AiService] ← OpenRouter chat OK feature='{}' userId={} in={} out={}",
                    feature, user.getId(), inputTokens, outputTokens);
            aiUsageLogRepository.save(AiUsageLog.builder()
                    .userId(user.getId())
                    .feature(feature)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .build());
            try {
                return objectMapper.readTree(rawContent);
            } catch (Exception parseEx) {
                log.error("[AiService] Chat model returned non-JSON content: {}", rawContent, parseEx);
                throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                        "The AI returned an unexpected format. Please try again.", parseEx);
            }
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiException.ErrorCode.AI_SERVICE_ERROR,
                    "Failed to parse the chat AI response. Please try again.", e);
        }
    }

    /**
     * CHAT-01: validates and enforces per-turn input length limits, then builds
     * the orchestration prompt for a chat turn.
     */
    private String buildChatPrompt(AiRequest r) {
        String userMessage = null;
        List<AiRequest.ChatMessage> history = r.getChatHistory();
        if (history != null && !history.isEmpty()) {
            AiRequest.ChatMessage last = history.get(history.size() - 1);
            userMessage = last.getContent();
        }
        if (userMessage != null && userMessage.length() > MAX_CHAT_INPUT_CHARS) {
            throw new BadRequestException(
                    "Your message is too long (" + userMessage.length() +
                            " chars). Max allowed is " + MAX_CHAT_INPUT_CHARS + ".");
        }
        String context = r.getChatResumeContext();
        if (context != null && context.length() > MAX_CHAT_CONTEXT_CHARS) {
            throw new BadRequestException(
                    "The collected resume context is too large. Please clear the conversation and start fresh.");
        }

        return "You are an expert, empathetic AI resume-building coach guiding the user to build a professional " +
                "resume through conversation. You have already collected this resume information from the user " +
                "(KNOWLEDGE SO FAR):\n" + orEmpty(context) + "\n\n" +
                "ONLY use information the user has actually provided. NEVER invent skills, experience, projects, " +
                "achievements, certifications, education, job titles, companies, metrics, or responsibilities. " +
                "If you are missing something, ask for it instead of guessing.\n\n" +
                "You are building the resume by gathering, in priority order, the sections the user actually has: " +
                "personal info, career summary, education, skills, experience, internships, projects, certifications, " +
                "achievements, and additional info. Do NOT force sections the user does not have — skip irrelevant ones. " +
                "Ask ONE relevant question at a time to fill the most important remaining gaps. " +
                "Do not repeat a question already answered or already asked (see the history). " +
                "When you have enough information to build a strong resume, tell the user and offer to generate it.\n\n" +
                "Conversation must be honest and truthful. Respond with ONLY JSON:\n" +
                "{\"reply\": \"your next question or response to the user\", " +
                "\"readyToGenerate\": true/false, \"missingSections\": [\"section names still missing\"], " +
                "\"collectedInfo\": {\"updated context blob summarizing all info gathered so far — keep it concise but complete\"}}\n\n" +
                "Keep reply concise (under " + MAX_CHAT_OUTPUT_CHARS + " chars) and focused on continuing the conversation.";
    }

    /**
     * CHAT-01: builds the prompt that converts the collected conversation
     * context into structured resume JSON. Strict no-hallucination rule: the
     * output may ONLY contain information the user actually provided.
     */
    private String buildChatGeneratePrompt(AiRequest r) {
        String context = r.getChatResumeContext();
        return "Convert the resume information collected from this user into a structured resume object.\n\n" +
                "=== Information collected from the user ===\n" + orEmpty(context) + "\n\n" +
                "STRICT RULE: Output ONLY information the user actually provided. Never invent skills, experience, " +
                "projects, achievements, certifications, education, job titles, companies, metrics, or responsibilities. " +
                "Leave a string field empty or an array field empty if the user did not provide that information. " +
                "Write the summary and any bullet points using ONLY the user's own facts, professionally reworded.\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text:\n" +
                "{\"fullName\": \"\", \"professionalTitle\": \"\", \"email\": \"\", \"phone\": \"\", " +
                "\"location\": \"\", \"summary\": \"\", " +
                "\"skills\": [\"skill\"], " +
                "\"experience\": [{\"company\": \"\", \"role\": \"\", \"location\": \"\", \"startDate\": \"\", \"endDate\": \"\", \"summary\": \"\", \"bullets\": [\"bullet\"]}], " +
                "\"projects\": [{\"name\": \"\", \"role\": \"\", \"techStack\": \"\", \"description\": \"\", \"highlights\": [\"highlight\"]}], " +
                "\"education\": [{\"institution\": \"\", \"degree\": \"\", \"field\": \"\", \"grade\": \"\", \"startDate\": \"\", \"endDate\": \"\", \"details\": \"\"}], " +
                "\"certifications\": [{\"name\": \"\", \"issuer\": \"\", \"year\": \"\"}], " +
                "\"achievements\": [\"achievement\"]}";
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

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
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

    // REWRITE-01: dedicated prompt for the improved Rewrite Text feature.
    // It rewrites only the user-provided text for the chosen resume section in
    // the chosen style, with strict no-invention rules. The old prompt's
    // "quantify achievements where possible" implicitly encouraged the model
    // to fabricate metrics; the new prompt forbids adding any fact, number, or
    // skill that was not present in the user's original text.
    private String buildRewritePrompt(AiRequest r) {
        String section = orEmpty(r.getResumeSection());
        if (!hasText(section)) section = "Other";
        String style = orEmpty(r.getRewriteStyle());
        if (!hasText(style)) style = "Professional";

        return "You are an expert professional resume writer. Rewrite the following " +
            "resume text for the \"" + section + "\" section, in a \"" + style + "\" style. " +
            "Make it clear, concise, professional, and ATS-friendly.\n\n" +
            "=== User-provided original text ===\n" +
            orEmpty(r.getText()) + "\n\n" +
            "=== Task ===\n" +
            "Rewrite the text so that:\n" +
            "- Grammar and sentence structure are improved.\n" +
            "- Wording is clear, strong, and professional for a resume.\n" +
            "- The text is concise and easy to read (ATS-friendly).\n" +
            "- The original meaning and every fact are preserved exactly.\n" +
            "- The style matches the requested \"" + style + "\" style.\n\n" +
            "=== CRITICAL — No-Invention Rules (do not violate) ===\n" +
            "- Use ONLY the words, facts, and information in the original text.\n" +
            "- NEVER add or invent information.\n" +
            "- NEVER add new skills, technologies, certifications, or tools.\n" +
            "- NEVER invent achievements, responsibilities, experience, or dates.\n" +
            "- NEVER invent numbers, percentages, metrics, or results.\n" +
            "- Example: \"Created a website using React.\" must become something like " +
            "\"Developed a website using React.\" — never \"Increased user engagement by 40%\" " +
            "because that number was not provided.\n" +
            "- Avoid unnecessary buzzwords; keep language direct and professional.\n\n" +
            "=== Output format ===\n" +
            "Respond with ONLY valid JSON matching exactly this schema, no other text (no " +
            "Markdown, no code fences, no surrounding prose):\n" +
            "{\"originalText\":\"<the exact original text you were given>\",\"rewrittenText\":\"<only the rewritten text>\"}";
    }

    // BULLETS-01: reworked prompt for the improved Bullet Points feature.
    // Uses the structured fields the new frontend form sends and enforces
    // strict no-hallucination rules. The old generic "quantify results
    // where possible" instruction implicitly encouraged the model to
    // invent metrics; the new prompt only allows user-provided outcomes.
    private String buildBulletPrompt(AiRequest r) {
        int numBullets = (r.getNumBullets() != null
                && r.getNumBullets() >= 1 && r.getNumBullets() <= 5)
            ? r.getNumBullets() : 5;

        String sectionType = orEmpty(r.getSectionType());
        String role        = orEmpty(r.getRole());
        String company     = orEmpty(r.getCompany());

        String description = !hasText(r.getDescription())
            ? joinOrNone(r.getResponsibilities())
            : r.getDescription().trim();

        return "You are an expert professional resume writer. Write strong, concise, " +
            "ATS-friendly resume bullet points for a resume " +
            "section of type \"" + sectionType + "\".\n\n" +
            "=== Context (all user-provided) ===\n" +
            "Section type: " + sectionType + "\n" +
            "Role / position: " + role + "\n" +
            "Organization / company (optional): " + company + "\n" +
            "What the user did (in their own words): " + orEmpty(description) + "\n" +
            "Technologies / tools the user actually used: " + joinOrNone(r.getTechnologies()) + "\n" +
            "Result / outcome (optional): " + orEmpty(r.getOutcome()) + "\n" +
            "Metrics (optional, only what the user reported): " + orEmpty(r.getMetrics()) + "\n\n" +
            "=== Task ===\n" +
            "Write exactly " + numBullets + " professional resume bullet point(s) that " +
            "improve and polish the user's information while preserving its factual meaning.\n\n" +
            "Each bullet should follow, when the information permits:\n" +
            "  Action verb + what was done + technology/method + result/impact.\n" +
            "Use strong action verbs. Make each bullet concise, professional, and " +
            "ATS-friendly. Highlight relevant technical skills naturally. Use different " +
            "action verbs across bullets; avoid repeating the same verb. " +
            "Do not use first-person pronouns such as \"I\", \"me\", or \"my\". " +
            "Avoid unnecessary buzzwords.\n\n" +
            "=== CRITICAL — No-Hallucination Rules (do not violate) ===\n" +
            "- Use ONLY the information explicitly provided above.\n" +
            "- NEVER invent achievements, numbers, percentages, features, responsibilities, " +
            "projects, company information, users/customers, or performance improvements.\n" +
            "- NEVER assume a technology was used merely because it is common for the role — " +
            "mention technologies only if the user listed them.\n" +
            "- If \"Metrics\" and/or \"Result / outcome\" are \"(not provided)\", do not add " +
            "any numbers or results — simply omit the result/impact part of the bullet.\n" +
            "- Do not exaggerate or embellish beyond what was stated.\n" +
            "- If \"organization / company\" is \"(not provided)\", do not name or imply an employer.\n\n" +
            "=== Output format ===\n" +
            "Respond with ONLY valid JSON matching exactly this schema, no other text (no " +
            "Markdown, no code fences, no surrounding prose):\n" +
            "{\"bullets\": [{\"text\": \"generated resume bullet point\", \"keywords\": [\"keyword1\", \"keyword2\"]}]}\n" +
            "(\"bullets\" must contain exactly " + numBullets + " item(s). \"keywords\" should " +
            "list the relevant technologies/skills that actually appear in each bullet — only " +
            "technologies the user provided.)";
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

    // SKILLS-02: reworked prompt for the improved Suggest Skills feature.
    // It analyzes the user's existing skills + resume information + optional
    // target job description/role, classifies every suggested skill, and
    // strictly separates skills the user actually has from ones that are only
    // mentioned in the job description (which must NOT be auto-added to the
    // resume). The old prompt returned a flat keyword list that did not
    // distinguish demonstrated skills from job-only skills.
    private String buildSkillsPrompt(AiRequest r) {
        return "You are assisting with resume skill analysis. Analyze only the information " +
            "provided by the user. Never claim that the user possesses a skill unless the " +
            "provided information supports it. Separate existing/demonstrated skills from " +
            "job-relevant skills that are not demonstrated. Do not fabricate skills, " +
            "experience, certifications, proficiency levels, or achievements.\n\n" +
            "=== User-provided information ===\n" +
            "Target job role (optional): " + orEmpty(r.getTargetRole()) + "\n" +
            "Skill category filter: " + orEmpty(r.getSkillCategory()) + "\n" +
            "Current skills the user stated they know: " + joinOrNone(r.getCurrentSkills()) + "\n" +
            "Resume information / content provided by the user:\n" + orEmpty(r.getResumeInformation()) + "\n" +
            "Target job description (optional):\n" + orEmpty(r.getJobDescription()) + "\n\n" +
            "=== Task ===\n" +
            "Identify and classify the relevant skills into the four groups below. Analyze " +
            "the user's existing skills, resume content, projects, experience, and education " +
            "along with the target job description and role.\n\n" +
            "1. existingSkills — skills the user explicitly stated they know/use. " +
            "Reason: \"Explicitly listed by the user\" (or similar).\n" +
            "2. demonstratedSkills — skills not explicitly listed but reasonably demonstrated " +
            "through the user's projects, experience, or other resume information. Reason: " +
            "explain which project/experience supports it. Do not treat every technology " +
            "mentioned in a project as expert-level knowledge; only claim it is demonstrated " +
            "if the provided information reasonably supports it.\n" +
            "3. jobRelevantSkills — skills that appear relevant to the target " +
            "job description/role but for which there is insufficient evidence that the user " +
            "currently possesses them. Reason: mention that it appears in the job " +
            "description but is not demonstrated.\n" +
            "4. recommendedSkills — a flat list of skill names containing ONLY the skills the " +
            "user's information supports (existing + demonstrated). NEVER include " +
            "job-relevant-but-not-demonstrated skills here.\n\n" +
            "=== Relevance rules ===\n" +
            "- Prioritize: direct match with existing skills, then skills demonstrated through " +
            "projects/experience, then frequently required skills in the job description, then " +
            "closely related skills the user's experience supports.\n" +
            "- When a skill category is provided (e.g. \"Programming Languages\"), focus the " +
            "suggestions on that category (\"All Relevant Skills\" means no restriction).\n" +
            "- Do not suggest unrelated technologies simply because they are popular.\n" +
            "- Never infer advanced skills from basic knowledge. Never claim proficiency that " +
            "was not demonstrated.\n\n" +
            "=== CRITICAL — No-Hallucination Rules (do not violate) ===\n" +
            "- NEVER invent skills.\n" +
            "- NEVER assume the user knows a technology.\n" +
            "- Do NOT add job-description skills to recommendedSkills automatically.\n" +
            "- Do NOT treat every technology mentioned in a project as expert-level knowledge.\n" +
            "- Do NOT recommend false certifications or fake experience.\n" +
            "- Example: user lists \"Java, SQL, HTML, CSS\"; job description lists \"Java, " +
            "Spring Boot, Docker, AWS, Kubernetes\". existingSkills contains Java, SQL, HTML, " +
            "CSS. jobRelevantSkills contains Spring Boot, Docker, AWS, " +
            "Kubernetes. recommendedSkills must NOT include Spring Boot, Docker, AWS, " +
            "Kubernetes.\n\n" +
            "=== Output format ===\n" +
            "Respond with ONLY valid JSON matching exactly this schema, no other text (no " +
            "Markdown, no code fences, no surrounding prose):\n" +
            "{\"existingSkills\":[\"Java\",\"SQL\"], " +
            "\"demonstratedSkills\":[\"REST API\"], " +
            "\"jobRelevantSkills\":[\"Spring Boot\",\"Docker\"], " +
            "\"recommendedSkills\":[\"Java\",\"SQL\",\"REST API\"]}\n" +
            "Those four top-level keys are required; output only valid JSON. " +
            "recommendedSkills must contain ONLY existing + demonstrated skill names.";
    }

    private String buildTailorPrompt(AiRequest r) {
        // TAILOR-01: analyze the resume against the job description and suggest
        // improvements. The AI must NEVER add information that does not exist in
        // the original resume — no fabricated skills, technologies, experience,
        // projects, achievements, certifications, metrics, or responsibilities.
        // Skills the job requires but the user lacks are reported as MISSING
        // keywords, never inserted into the resume.
        return "You are an expert resume tailoring analyst. Analyze the candidate's resume " +
                "against the target job description and suggest truthful improvements.\n\n" +
                "IMPORTANT RULE: Never add information that does not exist in the original resume. " +
                "Do NOT invent skills, technologies, experience, projects, achievements, " +
                "certifications, metrics, or responsibilities. If the job requires a skill the user " +
                "does not have, list it as a MISSING keyword instead of adding it to the resume.\n\n" +
                "=== Target job title ===\n" + orEmpty(r.getTargetRole()) + "\n\n" +
                "=== Job description ===\n" + orEmpty(r.getJobDescription()) + "\n\n" +
                "=== Complete resume content ===\n" + orEmpty(r.getTailorResumeInfo()) + "\n\n" +
                "=== Improve (truthfully) ===\n" +
                "1. Resume summary: reword it to emphasize the most job-relevant experience and skills " +
                "already present, without adding new claims.\n" +
                "2. Skills presentation: reorder/group the candidate's existing skills to surface the " +
                "ones most relevant to the job. Only include skills already in the resume.\n" +
                "3. Experience/project bullets: reword existing bullets to better highlight job-relevant " +
                "responsibilities and impact, without inventing responsibilities, metrics, or projects.\n" +
                "4. Relevant keywords: identify keywords already present in the resume that match the job.\n" +
                "5. Suggestions: practical, honest tips to improve job relevance.\n\n" +
                "=== Output format ===\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text, no Markdown, no code fences:\n" +
                "{\"tailoredSummary\": \"improved summary\", " +
                "\"tailoredSkills\": [\"existing skill\"], " +
                "\"tailoredBullets\": [\"improved resume bullet\"], " +
                "\"matchingKeywords\": [\"Java\"], " +
                "\"missingKeywords\": [\"Spring Boot\"], " +
                "\"suggestions\": [\"improvement suggestion\"]}\n" +
                "\"tailoredSkills\" must contain ONLY skills already present in the resume. " +
                "\"missingKeywords\" lists job-description skills absent from the resume (do not add them to the resume).";
    }

    private String buildAtsScorePrompt(AiRequest r) {
        // ATS-01: uses the full resume content + full job description + target
        // job title. The AI returns ONLY the six factor scores (0-100) plus
        // keyword/strength/improvement data. It must NOT decide a final score —
        // the backend computes the weighted final score from the six factors.
        return "You are an expert ATS (Applicant Tracking System) resume analyst. " +
                "Evaluate how well the candidate's resume matches the target job. " +
                "Score the match against SIX defined factors, each from 0 to 100. " +
                "Do NOT produce a final overall score — the system calculates the " +
                "weighted final score from your six factor scores.\n\n" +
                "=== Target job title ===\n" + orEmpty(r.getTargetRole()) + "\n\n" +
                "=== Full resume content ===\n" + orEmpty(r.getResumeText()) + "\n\n" +
                "=== Full job description ===\n" + orEmpty(r.getJobDescription()) + "\n\n" +
                "=== The six factors (score each 0-100; the weight shown is how the final score weights them) ===\n" +
                "1. keywordMatch (30%): overlap of important, distinctive keywords between resume and job description.\n" +
                "2. skillsMatch (25%): how many of the job description's required technical skills appear in the resume.\n" +
                "3. experienceRelevance (15%): how relevant the candidate's experience and role history are to the job.\n" +
                "4. educationMatch (10%): whether the resume's education satisfies the job's qualification requirements.\n" +
                "5. structureReadability (10%): resume structure, clarity, and ATS-friendliness (clear headings, bullets, no tables/images).\n" +
                "6. jobAlignment (10%): overall alignment of the resume as a whole to the job description.\n\n" +
                "=== Also provide ===\n" +
                "- matchingKeywords: string array of keywords/skills present in BOTH the resume and the job description.\n" +
                "- missingKeywords: string array of important keywords/skills from the job description that are ABSENT from the resume.\n" +
                "- strengths: string array of the strongest areas of the resume given the job description.\n" +
                "- improvements: string array of practical, truthful suggestions (sharpen the summary, add relevant project details, strengthen experience bullets, use section-relevant keywords, improve structure).\n\n" +
                "=== Rules ===\n" +
                "- Base every factor score strictly on evidence actually present in the resume. Do not inflate scores.\n" +
                "- Never invent facts about the candidate and never recommend fabricating information.\n" +
                "- For missingKeywords that are skills the candidate has not demonstrated, add a note in improvements " +
                "that the candidate should only add them if they genuinely possess them.\n\n" +
                "=== Output format ===\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text, no Markdown, no code fences:\n" +
                "{\"keywordMatch\":80,\"skillsMatch\":90,\"experienceRelevance\":70,\"educationMatch\":100,\"structureReadability\":85,\"jobAlignment\":75," +
                "\"matchingKeywords\":[\"Java\"],\"missingKeywords\":[\"Spring Boot\"],\"strengths\":[\"Strong alignment with Java\"],\"improvements\":[\"Sharpen the summary for this role\"]}";
    }

    private String buildCoverLetterPrompt(AiRequest r) {
        // COVER-01: generate a professional, personalized cover letter from the
        // user's actual resume information and job description. The AI must NEVER
        // invent experience, achievements, metrics, skills, certifications, or
        // company information — it may only use what the user provided.
        return "You are an expert cover letter writer. Write a professional, " +
                "personalized cover letter based SOLELY on the candidate's resume " +
                "information and the job description provided. " +
                "Never invent or add any information the user has not provided.\n\n" +
                "IMPORTANT RULE: Do NOT add fake experience, achievements, metrics, " +
                "skills, certifications, or company information. Use ONLY the user's " +
                "provided information. If something is not provided, do not make it up.\n\n" +
                "=== Candidate name ===\n" + orEmpty(r.getCandidateName()) + "\n\n" +
                "=== Job title ===\n" + orEmpty(r.getTargetRole()) + "\n\n" +
                "=== Company name (optional) ===\n" + orEmpty(r.getCompanyName()) + "\n\n" +
                "=== Job description ===\n" + orEmpty(r.getJobDescription()) + "\n\n" +
                "=== Resume / profile information ===\n" + orEmpty(r.getCoverResumeInfo()) + "\n\n" +
                "=== Additional information (optional) ===\n" + orEmpty(r.getAdditionalInfo()) + "\n\n" +
                "=== Cover letter requirements ===\n" +
                "- Be customized to the job description.\n" +
                "- Highlight relevant skills from the resume.\n" +
                "- Mention relevant projects or experience when provided.\n" +
                "- Explain why the candidate is a good fit for the role.\n" +
                "- Use professional, natural language.\n" +
                "- Avoid unnecessary repetition.\n" +
                "- Be concise (3-4 short paragraphs plus greeting and sign-off).\n" +
                "- Be directly usable by the user.\n\n" +
                "=== Output format ===\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text, no Markdown, no code fences:\n" +
                "{\"coverLetter\": \"the complete cover letter text\"}";
    }

    private String buildLinkedInPrompt(AiRequest r) {
        // LINKEDIN-01: generate recruiter-friendly LinkedIn content from the
        // user's actual resume/profile information. The AI must NEVER invent
        // skills, experience, achievements, certifications, or metrics — it may
        // only use what the user provided. Returns headline, about, skills
        // (relevant to the target role), and profile improvement suggestions.
        return "You are an expert LinkedIn profile optimizer for job seekers. " +
                "Generate professional, recruiter-friendly LinkedIn content based SOLELY " +
                "on the resume and profile information the user provides. " +
                "Never invent or add any information the user has not provided.\n\n" +
                "IMPORTANT RULE: Do NOT add skills the user does not have. " +
                "Do NOT fabricate experience, achievements, job titles, certifications, " +
                "or metrics. Use ONLY the user's provided information.\n\n" +
                "=== Target job role ===\n" + orEmpty(r.getTargetRole()) + "\n\n" +
                "=== Resume / profile information ===\n" + orEmpty(r.getLinkedinResumeInfo()) + "\n\n" +
                "=== Existing LinkedIn content (optional — improve if provided) ===\n" +
                orEmpty(r.getLinkedinExistingContent()) + "\n\n" +
                "=== Generate the following ===\n" +
                "1. Headline: A concise professional headline containing relevant skills and the target role. " +
                "Keep it under 220 characters. Use natural keywords recruiters search for.\n" +
                "2. About: A professional About section (3-5 short paragraphs) that clearly describes " +
                "the candidate, highlights relevant skills, mentions projects/experience when provided, " +
                "shows career interests, uses natural keywords, and is suitable for LinkedIn. " +
                "Do not exaggerate experience.\n" +
                "3. Skills: A list of the most relevant skills from the user's profile for the target role. " +
                "Use only skills the user actually has.\n" +
                "4. Suggestions: 3-5 practical profile improvement suggestions based on the provided information.\n\n" +
                "=== Output format ===\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text, no Markdown, no code fences:\n" +
                "{\"headline\": \"...\", \"about\": \"...\", \"skills\": [\"Java\", \"SQL\"], " +
                "\"suggestions\": [\"Add your strongest project to the Featured section\"]}";
    }

    private String buildGrammarCheckPrompt(AiRequest r) {
        // GRAMMAR-01: check grammar, spelling, punctuation, and sentence structure
        // while preserving the original meaning and staying appropriate for a
        // resume. The AI must NEVER invent or add information (no new skills,
        // technologies, achievements, metrics, experience, responsibilities, or
        // certifications) — it only corrects/improves the provided text. Returns a
        // structured list of issues (original/correction/reason).
        return "You are an expert resume editor. Check the provided text for grammar, " +
                "spelling, punctuation, and sentence-structure problems. Improve clarity " +
                "where necessary while PRESERVING the original meaning. Keep the writing " +
                "professional and suitable for a resume. Avoid unnecessary rewriting.\n\n" +
                "IMPORTANT RULE: Do NOT invent or add any information. Do NOT add new skills, " +
                "technologies, achievements, metrics, experience, responsibilities, or " +
                "certifications. Only correct or improve the information the user provided.\n\n" +
                "=== Resume section this text belongs to ===\n" + orEmpty(r.getGrammarSection()) + "\n\n" +
                "=== Text to check ===\n" + orEmpty(r.getText()) + "\n\n" +
                "=== Output format ===\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text, no Markdown, no code fences:\n" +
                "{\n" +
                "  \"originalText\": \"the original user text, unchanged\",\n" +
                "  \"correctedText\": \"the full corrected text (identical to originalText if there are no issues)\",\n" +
                "  \"issues\": [\n" +
                "    { \"original\": \"the incorrect snippet\", \"correction\": \"the corrected snippet\", \"reason\": \"Grammar correction\" }\n" +
                "  ]\n" +
                "}\n" +
                "If there are NO issues, return an empty \"issues\" array and keep \"correctedText\" " +
                "identical to \"originalText\".";
    }

    private String buildInterviewPrepPrompt(AiRequest r) {
        // INTERVIEW-01: generate personalized interview preparation questions
        // based on the candidate's actual resume, the target role/job description,
        // interview type, experience level, and requested number of questions.
        // NEVER invent background: project questions use only the projects the
        // user provided, and technical questions must not claim knowledge of
        // technologies the user did not list. Technologies in the job description
        // that the user does not have may appear only as clearly-labeled
        // job-relevant preparation topics.
        String type = orEmpty(r.getInterviewType());
        String expLevel = orEmpty(r.getExperienceLevel());
        Integer count = r.getQuestionCount();
        int n = (count == null || count < 1) ? 10 : count;
        if (n > 30) n = 30;

        return "You are an expert interview coach. Based on the candidate's ACTUAL background and " +
                "the target job, generate " + n + " personalized interview preparation questions with " +
                "model answers.\n\n" +
                "IMPORTANT RULE: Never invent information about the user's background. " +
                "Use only the projects, experience, and skills provided by the user. " +
                "For technical questions, do not claim the user knows a technology they did not provide. " +
                "If the job description mentions a technology the user does not have, you may include it " +
                "as a clearly-labeled 'job-relevant preparation topic', but do not claim the user knows it.\n\n" +
                "=== Target job role ===\n" + orEmpty(r.getTargetRole()) + "\n\n" +
                "=== Company ===\n" + orEmpty(r.getCompanyName()) + "\n\n" +
                "=== Job description ===\n" + orEmpty(r.getJobDescription()) + "\n\n" +
                "=== Interview type ===\n" + (type.isEmpty() ? "Mixed" : type) + "\n\n" +
                "=== Experience level ===\n" + (expLevel.isEmpty() ? "Experienced" : expLevel) + "\n\n" +
                "=== Number of questions ===\n" + n + "\n\n" +
                "=== Candidate's full resume ===\n" + orEmpty(r.getInterviewResumeInfo()) + "\n\n" +
                "=== Question categories by interview type ===\n" +
                "Technical: Programming, OOP, SQL, Frameworks, Projects, Problem solving. " +
                "Only include technologies supported by the user's information or job description context.\n" +
                "HR / Behavioral: Introduction, Strengths, Weaknesses, Career goals, Teamwork, Challenges.\n" +
                "Project questions: Project objective, Technologies used, Your contribution, Challenges, Solutions, Improvements (use only the user's provided projects).\n" +
                "Mixed: a blend of the above.\n\n" +
                "For each question provide a question, category, difficulty (Easy/Medium/Hard), a " +
                "suggested answer based on the user's provided information, and key points to mention.\n\n" +
                "Respond with ONLY valid JSON matching exactly this schema, no other text, no Markdown, no code fences:\n" +
                "{\"questions\": [{\"question\": \"...\", \"category\": \"Technical\", " +
                "\"difficulty\": \"Easy\", \"suggestedAnswer\": \"...\", \"keyPoints\": [\"...\"]}], " +
                "\"generalTips\": \"1-2 sentences of general interview advice\"}\n" +
                "Provide exactly " + n + " questions. All answers must be based on the user's actual resume content.";
    }
}
