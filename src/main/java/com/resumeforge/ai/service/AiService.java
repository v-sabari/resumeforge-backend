package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.AiBulletsRequest;
import com.resumeforge.ai.dto.AiListResponse;
import com.resumeforge.ai.dto.AiRewriteRequest;
import com.resumeforge.ai.dto.AiSkillsRequest;
import com.resumeforge.ai.dto.AiSummaryRequest;
import com.resumeforge.ai.dto.AiTextResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

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

    public AiService(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public AiTextResponse generateSummary(AiSummaryRequest request) {
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
                - Be ATS-friendly
                - Avoid fake metrics, fake companies, or invented claims
                - Keep it impactful and natural
                - Return plain text only
                """.formatted(
                safe(request.targetRole()),
                joinList(request.skills()),
                joinList(request.achievements()),
                joinList(request.highlights()),
                safe(request.currentSummary())
        );

        return new AiTextResponse(callAi(prompt, 220));
    }

    public AiListResponse generateBullets(AiBulletsRequest request) {
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

        String response = callAi(prompt, 320);

        List<String> bullets = response.lines()
                .map(String::trim)
                .map(this::stripBulletPrefix)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(5)
                .toList();

        return new AiListResponse(bullets);
    }

    public AiListResponse suggestSkills(AiSkillsRequest request) {
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

        String response = callAi(prompt, 180);

        List<String> skills = Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(12)
                .toList();

        return new AiListResponse(skills);
    }

    public AiTextResponse rewrite(AiRewriteRequest request) {
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

        return new AiTextResponse(callAi(prompt, 220));
    }

    private String callAi(String prompt, int maxTokens) {
        validateConfiguration();

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", """
                                            You are an expert resume writing assistant.
                                            Write professional, ATS-friendly, concise output.
                                            Never invent fake experience, fake employers, fake certifications, or fake achievements.
                                            Return only the requested content with no extra commentary.
                                            """
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
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
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("No error body returned from OpenRouter")
                                    .map(body -> new RuntimeException(
                                            "OpenRouter API error: HTTP " + clientResponse.statusCode().value() + " - " + body
                                    ))
                    )
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw new RuntimeException("Empty response received from OpenRouter");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("OpenRouter response missing choices: " + responseBody);
            }

            JsonNode contentNode = choices.get(0).path("message").path("content");
            String content = contentNode.asText("").trim();

            if (content.isBlank()) {
                throw new RuntimeException("OpenRouter returned blank content: " + responseBody);
            }

            return content;
        } catch (Exception e) {
            throw new RuntimeException("AI generation failed: " + e.getMessage(), e);
        }
    }

    private void validateConfiguration() {
        if (isBlank(apiKey)) {
            throw new RuntimeException("OpenRouter API key is missing");
        }
        if (isBlank(baseUrl)) {
            throw new RuntimeException("OpenRouter base URL is missing");
        }
        if (isBlank(model)) {
            throw new RuntimeException("OpenRouter model is missing");
        }
        if (isBlank(siteUrl)) {
            throw new RuntimeException("OpenRouter site URL is missing");
        }
        if (isBlank(siteName)) {
            throw new RuntimeException("OpenRouter site name is missing");
        }
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private String stripBulletPrefix(String value) {
        return value
                .replaceFirst("^[-•*]\\s*", "")
                .replaceFirst("^\\d+[.)]\\s*", "")
                .trim();
    }
}