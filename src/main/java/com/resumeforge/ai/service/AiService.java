package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.AiRequest;
import com.resumeforge.ai.dto.AiResponse;
import com.resumeforge.ai.entity.AiUsageLog;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.RateLimitException;
import com.resumeforge.ai.repository.AiUsageLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AiService {

    // B6 FIX: per-user daily limits
    // Free users: 5 AI calls per day
    // Premium users: 50 AI calls per day
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // B10 FIX: @Transactional removed (conflicts with @Async proxy).
    // @Async("aiTaskExecutor") offloads the blocking RestTemplate call to the
    // dedicated ai-async-* thread pool defined in AsyncConfig, freeing Tomcat
    // threads to serve other requests while OpenRouter call is in-flight.

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> rewriteContent(User user, AiRequest request) {
        return callOpenRouter(user, "rewrite", buildRewritePrompt(request.getContent()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> improveBullets(User user, AiRequest request) {
        return callOpenRouter(user, "bullets", buildBulletPrompt(request.getContent()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> generateSummary(User user, AiRequest request) {
        return callOpenRouter(user, "summary", buildSummaryPrompt(request.getContent(), request.getContext()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> extractSkills(User user, AiRequest request) {
        return callOpenRouter(user, "skills", buildSkillsPrompt(request.getContent()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> tailorToJob(User user, AiRequest request) {
        return callOpenRouter(user, "tailor", buildTailorPrompt(request.getContent(), request.getJobDescription()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> atsScore(User user, AiRequest request) {
        return callOpenRouter(user, "ats_score", buildAtsScorePrompt(request.getContent(), request.getJobDescription()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> generateCoverLetter(User user, AiRequest request) {
        return callOpenRouter(user, "cover_letter", buildCoverLetterPrompt(request.getContent(), request.getJobDescription()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> optimizeLinkedIn(User user, AiRequest request) {
        return callOpenRouter(user, "linkedin", buildLinkedInPrompt(request.getContent()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> checkGrammar(User user, AiRequest request) {
        return callOpenRouter(user, "grammar_check", buildGrammarCheckPrompt(request.getContent()));
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> generateInterviewPrep(User user, AiRequest request) {
        return callOpenRouter(user, "interview_prep", buildInterviewPrepPrompt(request.getContent(), request.getJobDescription()));
    }

    private CompletableFuture<AiResponse> callOpenRouter(User user, String feature, String prompt) {

        // B6 FIX: enforce per-user daily rate limit before calling the external API
        int limit = user.isPremium() ? PREMIUM_DAILY_LIMIT : FREE_DAILY_LIMIT;
        LocalDateTime startOfWindow = LocalDateTime.now().minusHours(24);
        long usageCount = aiUsageLogRepository.countByUserIdAndCreatedAtAfter(user.getId(), startOfWindow);

        if (usageCount >= limit) {
            String tier = user.isPremium() ? "Premium" : "Free";
            throw new RateLimitException(
                    tier + " plan limit reached (" + limit + " AI requests per day). " +
                            (user.isPremium() ? "Please try again tomorrow." : "Upgrade to Premium for higher limits.")
            );
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openRouterApiKey);
            headers.set("HTTP-Referer", siteUrl);
            headers.set("X-Title", siteName);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    openRouterBaseUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String result = responseJson.at("/choices/0/message/content").asText();

            Integer inputTokens  = responseJson.at("/usage/prompt_tokens").asInt(0);
            Integer outputTokens = responseJson.at("/usage/completion_tokens").asInt(0);

            // Log usage
            AiUsageLog log = AiUsageLog.builder()
                    .userId(user.getId())
                    .feature(feature)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .build();
            aiUsageLogRepository.save(log);

            return CompletableFuture.completedFuture(
                    AiResponse.builder()
                            .result(result)
                            .inputTokens(inputTokens)
                            .outputTokens(outputTokens)
                            .build()
            );

        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AI service error: " + e.getMessage());
        }
    }

    private String buildRewritePrompt(String content) {
        return "Rewrite the following resume content to be more professional, impactful, and ATS-friendly. " +
                "Use strong action verbs and quantify achievements where possible:\n\n" + content;
    }

    private String buildBulletPrompt(String content) {
        return "Improve these resume bullet points to be more impactful and ATS-friendly. " +
                "Start with strong action verbs, quantify achievements, and highlight results:\n\n" + content;
    }

    private String buildSummaryPrompt(String content, String context) {
        String basePrompt = "Create a professional resume summary that highlights key skills and achievements. " +
                "Make it compelling and ATS-friendly.";
        if (context != null && !context.isEmpty()) {
            basePrompt += " Context: " + context;
        }
        return basePrompt + "\n\nResume details:\n" + content;
    }

    private String buildSkillsPrompt(String content) {
        return "Extract and categorize technical and soft skills from the following resume content. " +
                "Return as a structured list:\n\n" + content;
    }

    private String buildTailorPrompt(String content, String jobDescription) {
        return "Tailor this resume content to match the following job description. " +
                "Emphasize relevant skills and experience while maintaining honesty:\n\n" +
                "Job Description:\n" + (jobDescription != null ? jobDescription : "Not provided") +
                "\n\nResume Content:\n" + content;
    }

    private String buildAtsScorePrompt(String content, String jobDescription) {
        return "Analyze this resume for ATS compatibility and job match. " +
                "Provide a score (0-100) and specific recommendations:\n\n" +
                "Job Description:\n" + (jobDescription != null ? jobDescription : "General analysis") +
                "\n\nResume:\n" + content;
    }

    private String buildCoverLetterPrompt(String content, String jobDescription) {
        return "Write a professional cover letter based on this resume and job description. " +
                "Make it personalized, enthusiastic, and highlight relevant qualifications:\n\n" +
                "Job Description:\n" + (jobDescription != null ? jobDescription : "Not provided") +
                "\n\nResume:\n" + content;
    }

    private String buildLinkedInPrompt(String content) {
        return "Optimize this resume content for LinkedIn. Create compelling sections for:\n" +
                "1. Headline\n2. About/Summary\n3. Experience highlights\n\nResume:\n" + content;
    }

    private String buildGrammarCheckPrompt(String content) {
        return "Check this resume content for grammar, spelling, and clarity issues. " +
                "Provide corrected version and explanation of changes:\n\n" + content;
    }

    private String buildInterviewPrepPrompt(String content, String jobDescription) {
        return "Based on this resume and job description, generate 10 likely interview questions " +
                "with suggested answers:\n\n" +
                "Job Description:\n" + (jobDescription != null ? jobDescription : "General questions") +
                "\n\nResume:\n" + content;
    }
}