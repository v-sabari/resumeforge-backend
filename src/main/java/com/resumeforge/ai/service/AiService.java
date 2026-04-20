package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.AiRequest;
import com.resumeforge.ai.dto.AiResponse;
import com.resumeforge.ai.entity.AiUsageLog;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.AiUsageLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiService {

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

    @Transactional
    public AiResponse rewriteContent(User user, AiRequest request) {
        String prompt = buildRewritePrompt(request.getContent());
        return callOpenRouter(user, "rewrite", prompt);
    }

    @Transactional
    public AiResponse improveBullets(User user, AiRequest request) {
        String prompt = buildBulletPrompt(request.getContent());
        return callOpenRouter(user, "bullets", prompt);
    }

    @Transactional
    public AiResponse generateSummary(User user, AiRequest request) {
        String prompt = buildSummaryPrompt(request.getContent(), request.getContext());
        return callOpenRouter(user, "summary", prompt);
    }

    @Transactional
    public AiResponse extractSkills(User user, AiRequest request) {
        String prompt = buildSkillsPrompt(request.getContent());
        return callOpenRouter(user, "skills", prompt);
    }

    @Transactional
    public AiResponse tailorToJob(User user, AiRequest request) {
        String prompt = buildTailorPrompt(request.getContent(), request.getJobDescription());
        return callOpenRouter(user, "tailor", prompt);
    }

    @Transactional
    public AiResponse atsScore(User user, AiRequest request) {
        String prompt = buildAtsScorePrompt(request.getContent(), request.getJobDescription());
        return callOpenRouter(user, "ats_score", prompt);
    }

    @Transactional
    public AiResponse generateCoverLetter(User user, AiRequest request) {
        String prompt = buildCoverLetterPrompt(request.getContent(), request.getJobDescription());
        return callOpenRouter(user, "cover_letter", prompt);
    }

    @Transactional
    public AiResponse optimizeLinkedIn(User user, AiRequest request) {
        String prompt = buildLinkedInPrompt(request.getContent());
        return callOpenRouter(user, "linkedin", prompt);
    }

    @Transactional
    public AiResponse checkGrammar(User user, AiRequest request) {
        String prompt = buildGrammarCheckPrompt(request.getContent());
        return callOpenRouter(user, "grammar_check", prompt);
    }

    @Transactional
    public AiResponse generateInterviewPrep(User user, AiRequest request) {
        String prompt = buildInterviewPrepPrompt(request.getContent(), request.getJobDescription());
        return callOpenRouter(user, "interview_prep", prompt);
    }

    private AiResponse callOpenRouter(User user, String feature, String prompt) {
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
            
            Integer inputTokens = responseJson.at("/usage/prompt_tokens").asInt(0);
            Integer outputTokens = responseJson.at("/usage/completion_tokens").asInt(0);

            // Log usage
            AiUsageLog log = AiUsageLog.builder()
                    .userId(user.getId())
                    .feature(feature)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .build();
            aiUsageLogRepository.save(log);

            return AiResponse.builder()
                    .result(result)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .build();

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
