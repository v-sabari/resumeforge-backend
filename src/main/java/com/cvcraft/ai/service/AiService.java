package com.cvcraft.ai.service;

import com.cvcraft.ai.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiService {

    public AiTextResponse generateSummary(AiSummaryRequest req) {
        String role      = safe(req.targetRole(), "professional");
        LinkedHashSet<String> kw = new LinkedHashSet<>();
        collect(kw, req.skills());
        collect(kw, req.achievements());
        collect(kw, req.highlights());
        String topKw    = kw.stream().limit(4).collect(Collectors.joining(", "));
        String base     = safe(req.currentSummary(), "");

        String summary = Stream.of(
                base.isBlank() ? null : base,
                "Results-driven " + role + " with a track record of delivering measurable outcomes, " +
                "leading cross-functional collaboration, and executing in fast-paced environments.",
                topKw.isBlank() ? null : "Proven strength in " + topKw +
                " — keeping work aligned to ATS-friendly language and clear business impact.",
                "Known for improving process efficiency, communicating with clarity, and building " +
                "concise resumes that highlight real value."
        ).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank())
         .collect(Collectors.joining(" "));

        return new AiTextResponse(trimWords(summary, 60));
    }

    public AiListResponse generateBullets(AiBulletsRequest req) {
        String role       = safe(req.role(), "professional");
        String company    = safe(req.company(), "the organisation");
        List<String> resp = normalise(req.responsibilities());
        List<String> tech  = normalise(req.technologies());

        LinkedHashSet<String> bullets = new LinkedHashSet<>();
        for (String r : resp.stream().limit(3).toList()) {
            bullets.add(trimWords(cap("Led " + r + " at " + company +
                    ", improving execution quality and stakeholder visibility through clear delivery ownership."), 22));
        }
        if (!tech.isEmpty()) {
            bullets.add(trimWords("Leveraged " + String.join(", ", tech.stream().limit(4).toList()) +
                    " to streamline workflows, reduce manual effort, and scale " + role.toLowerCase() + " output.", 22));
        }
        bullets.add(trimWords("Partnered with cross-functional teams to prioritise deliverables, " +
                "solve blockers quickly, and maintain consistent execution against deadlines.", 20));
        bullets.add(trimWords("Delivered improvements that strengthened process reliability, " +
                "user satisfaction, and measurable business performance.", 18));

        return new AiListResponse(bullets.stream().limit(5).toList());
    }

    public AiListResponse suggestSkills(AiSkillsRequest req) {
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        collect(skills, req.currentSkills());
        collect(skills, req.experienceKeywords());
        collect(skills, req.projectKeywords());

        String role = safe(req.targetRole(), "").toLowerCase(Locale.ROOT);
        if (role.contains("frontend") || role.contains("react")) {
            skills.addAll(List.of("React", "TypeScript", "Tailwind CSS", "REST APIs", "Vite", "Performance Optimisation"));
        } else if (role.contains("backend") || role.contains("spring") || role.contains("java")) {
            skills.addAll(List.of("Spring Boot", "Java", "PostgreSQL", "REST APIs", "JWT", "Docker", "System Design"));
        } else if (role.contains("fullstack") || role.contains("full stack")) {
            skills.addAll(List.of("React", "Node.js", "PostgreSQL", "REST APIs", "TypeScript", "CI/CD"));
        } else if (role.contains("product") || role.contains("pm")) {
            skills.addAll(List.of("Product Roadmapping", "Stakeholder Management", "A/B Testing", "Product Analytics", "User Research", "Agile"));
        } else if (role.contains("design") || role.contains("ux")) {
            skills.addAll(List.of("Figma", "User Research", "Prototyping", "Design Systems", "Usability Testing", "Wireframing"));
        } else if (role.contains("data") || role.contains("analyst")) {
            skills.addAll(List.of("Python", "SQL", "Tableau", "Data Visualisation", "Excel", "Statistical Analysis"));
        } else {
            skills.addAll(List.of("Communication", "Problem Solving", "Project Execution", "Process Improvement",
                    "Cross-functional Collaboration", "Time Management"));
        }
        return new AiListResponse(skills.stream().map(this::normaliseSkill).distinct().limit(12).toList());
    }

    public AiTextResponse rewrite(AiRewriteRequest req) {
        String tone = safe(req.tone(), "professional");
        String role = safe(req.targetRole(), "candidate");
        String text = safe(req.text(), "").replaceAll("\\s+", " ").trim();
        if (text.isBlank()) text = "Delivered meaningful results through reliable execution and quality.";

        String prefix = switch (tone.toLowerCase(Locale.ROOT)) {
            case "concise"   -> "Concise: ";
            case "impactful" -> "Impact-focused: ";
            default          -> "Professional: ";
        };

        String rewritten = (prefix + cap(text))
                .replace("worked on",  "delivered")
                .replace("helped",     "supported")
                .replace("was doing",  "executed")
                .replace("did",        "executed")
                .replace("made",       "developed")
                .replace("was part of","contributed to");

        return new AiTextResponse(trimWords(rewritten, 45));
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private void collect(Set<String> target, List<String> values) {
        if (values == null) return;
        values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).forEach(target::add);
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private List<String> normalise(List<String> values) {
        if (values == null) return Collections.emptyList();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).toList();
    }

    private String trimWords(String text, int maxWords) {
        if (text == null) return "";
        String[] parts = text.trim().split("\\s+");
        if (parts.length <= maxWords) return text.trim();
        return String.join(" ", Arrays.copyOfRange(parts, 0, maxWords)).trim() + "…";
    }

    private String cap(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String normaliseSkill(String skill) {
        return Arrays.stream(skill.split("\\s+"))
                .map(w -> w.isBlank() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}
