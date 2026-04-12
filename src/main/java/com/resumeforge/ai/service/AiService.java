package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.openrouter.api-key}")
    private String apiKey;

    @Value("${app.openrouter.base-url}")
    private String baseUrl;

    @Value("${app.openrouter.model}")
    private String model;

    @Value("${app.openrouter.site-url}")
    private String siteUrl;

    @Value("${app.openrouter.site-name}")
    private String siteName;

    public AiService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public AiTextResponse generateSummary(AiSummaryRequest request) {
        String prompt = """
            Write a professional ATS-optimized resume summary.

            Target Role: %s
            Skills: %s
            Achievements: %s

            Requirements:
            - 2 to 4 sentences
            - Professional tone
            - ATS-friendly
            - Concise and impactful
            """.formatted(
                request.targetRole(),
                request.skills(),
                request.achievements()
        );

        return new AiTextResponse(callAi(prompt));
    }

    public AiListResponse generateBullets(AiBulletsRequest request) {
        String prompt = """
            Generate 5 ATS-optimized resume bullet points.

            Role: %s
            Company: %s
            Responsibilities: %s
            Technologies: %s

            Requirements:
            - Strong action verbs
            - Quantified/impact-oriented where possible
            - Return each bullet on new line
            """.formatted(
                request.role(),
                request.company(),
                request.responsibilities(),
                request.technologies()
        );

        String response = callAi(prompt);

        List<String> bullets = response.lines()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        return new AiListResponse(bullets);
    }

    public AiListResponse suggestSkills(AiSkillsRequest request) {
        String prompt = """
            Suggest 12 ATS-friendly resume skills for this role.

            Target Role: %s
            Existing Skills: %s
            Experience Keywords: %s
            Project Keywords: %s

            Return only comma-separated skills.
            """.formatted(
                request.targetRole(),
                request.currentSkills(),
                request.experienceKeywords(),
                request.projectKeywords()
        );

        String response = callAi(prompt);

        List<String> skills = List.of(response.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(12)
                .toList();

        return new AiListResponse(skills);
    }

    public AiTextResponse rewrite(AiRewriteRequest request) {
        String prompt = """
            Rewrite this resume text professionally.

            Target Role: %s
            Tone: %s
            Text: %s

            Requirements:
            - Improve clarity
            - Improve professionalism
            - ATS-friendly
            - Preserve meaning
            """.formatted(
                request.targetRole(),
                request.tone(),
                request.text()
        );

        return new AiTextResponse(callAi(prompt));
    }

    private String callAi(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    )
            );

            String response = webClient.post()
                    .uri(baseUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("HTTP-Referer", siteUrl)
                    .header("X-Title", siteName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);

            return root.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText()
                    .trim();

        } catch (Exception e) {
            throw new RuntimeException("AI generation failed", e);
        }
    }
}