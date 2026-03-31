package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiService {

    public AiTextResponse generateSummary(AiSummaryRequest request) {
        String role = safe(request.targetRole(), "professional");
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        collect(keywords, request.skills());
        collect(keywords, request.achievements());
        collect(keywords, request.highlights());

        String topKeywords = keywords.stream().limit(4).collect(Collectors.joining(", "));
        String base = request.currentSummary();

        String summary = Stream.of(
                        base,
                        "Results-driven " + role + " with experience delivering measurable outcomes, cross-functional collaboration, and execution in fast-paced environments.",
                        topKeywords.isBlank() ? "" : "Brings hands-on strength in " + topKeywords + " while keeping work aligned to ATS-friendly language and business impact.",
                        "Known for improving efficiency, communicating clearly, and building concise one-page resumes that highlight value quickly."
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));

        return new AiTextResponse(trimWords(summary, 55));
    }

    public AiListResponse generateBullets(AiBulletsRequest request) {
        String role = safe(request.role(), "professional");
        String company = safe(request.company(), "the organization");
        List<String> responsibilities = normalize(request.responsibilities());
        List<String> technologies = normalize(request.technologies());
        String current = safe(request.currentText(), "");

        LinkedHashSet<String> bullets = new LinkedHashSet<>();
        if (!responsibilities.isEmpty()) {
            for (String responsibility : responsibilities.stream().limit(4).toList()) {
                bullets.add(trimWords(capitalize("Led " + responsibility + " for " + company + ", improving execution quality and stakeholder visibility through clear delivery ownership."), 24));
            }
        }
        if (!technologies.isEmpty()) {
            bullets.add(trimWords("Used " + String.join(", ", technologies.stream().limit(4).toList()) + " to streamline workflows, reduce manual effort, and support scalable " + role.toLowerCase() + " execution.", 24));
        }
        if (!current.isBlank()) {
            bullets.add(trimWords("Refined existing work into concise, metrics-ready resume language that emphasizes outcomes, collaboration, and operational impact.", 22));
        }
        bullets.add(trimWords("Partnered with cross-functional teams to prioritize deliverables, solve problems quickly, and maintain consistent execution against deadlines.", 22));
        bullets.add(trimWords("Delivered improvements that strengthened process reliability, user experience, and measurable business performance.", 18));

        return new AiListResponse(bullets.stream().limit(5).toList());
    }

    public AiListResponse suggestSkills(AiSkillsRequest request) {
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        collect(skills, request.currentSkills());
        collect(skills, request.experienceKeywords());
        collect(skills, request.projectKeywords());

        String role = safe(request.targetRole(), "").toLowerCase(Locale.ROOT);
        if (role.contains("frontend") || role.contains("react")) {
            skills.addAll(List.of("React", "TypeScript", "REST APIs", "Responsive Design", "Performance Optimization"));
        } else if (role.contains("backend") || role.contains("spring")) {
            skills.addAll(List.of("Spring Boot", "REST APIs", "MySQL", "JWT Authentication", "System Design"));
        } else if (role.contains("product")) {
            skills.addAll(List.of("Roadmapping", "Stakeholder Management", "Experimentation", "Product Analytics", "User Research"));
        } else {
            skills.addAll(List.of("Communication", "Problem Solving", "Project Execution", "Process Improvement", "Cross-functional Collaboration"));
        }

        return new AiListResponse(skills.stream().map(this::normalizeSkill).distinct().limit(12).toList());
    }

    public AiTextResponse rewrite(AiRewriteRequest request) {
        String tone = safe(request.tone(), "professional");
        String role = safe(request.targetRole(), "candidate");
        String text = safe(request.text(), "").replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            text = "Delivered meaningful results through reliable execution, collaboration, and attention to quality.";
        }

        String prefix = switch (tone.toLowerCase(Locale.ROOT)) {
            case "concise" -> "Concise rewrite for " + role + ": ";
            case "impactful" -> "Impact-focused rewrite for " + role + ": ";
            default -> "Professional rewrite for " + role + ": ";
        };

        String rewritten = prefix + capitalize(text)
                .replace("worked on", "delivered")
                .replace("helped", "supported")
                .replace("did", "executed")
                .replace("made", "developed");

        return new AiTextResponse(trimWords(rewritten, 45));
    }

    private void collect(Set<String> target, List<String> values) {
        if (values == null) return;
        values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).forEach(target::add);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private List<String> normalize(List<String> values) {
        if (values == null) return Collections.emptyList();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).toList();
    }

    private String trimWords(String text, int maxWords) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length <= maxWords) {
            return text.trim();
        }
        return String.join(" ", Arrays.copyOfRange(parts, 0, maxWords)).trim() + "…";
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String normalizeSkill(String skill) {
        return Arrays.stream(skill.split("\\s+"))
                .map(word -> word.isBlank() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
