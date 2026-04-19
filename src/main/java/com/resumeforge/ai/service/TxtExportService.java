package com.resumeforge.ai.service;

import com.resumeforge.ai.entity.Education;
import com.resumeforge.ai.entity.Experience;
import com.resumeforge.ai.entity.Project;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.ResumeRepository;
import com.resumeforge.ai.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates a plain-text (.txt) resume export.
 *
 * Use cases:
 *   - Pasting into ATS portals that only accept plain text
 *   - Sending via email to recruiters who request a text version
 *   - LinkedIn "Add to profile" copy-paste
 *
 * Format: clean, human-readable, uses standard section headers
 * that most ATS parsers recognize. No special characters beyond
 * ASCII dashes and pipe separators.
 */
@Service
public class TxtExportService {

    private final ResumeRepository resumeRepository;
    private final JsonUtil jsonUtil;

    public TxtExportService(ResumeRepository resumeRepository, JsonUtil jsonUtil) {
        this.resumeRepository = resumeRepository;
        this.jsonUtil          = jsonUtil;
    }

    @Transactional(readOnly = true)
    public byte[] generateResumeTxt(Long resumeId, User user) {
        Resume r = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        StringBuilder sb = new StringBuilder();

        // ── Name & title ────────────────────────────────────────────────
        sb.append(safe(r.getFullName()).toUpperCase()).append("\n");
        if (hasContent(r.getRole())) {
            sb.append(safe(r.getRole())).append("\n");
        }

        // ── Contact ──────────────────────────────────────────────────────
        String contact = joinPipe(r.getEmail(), r.getPhone(), r.getLocation(),
                r.getLinkedin(), r.getGithub(), r.getPortfolio());
        if (!contact.isBlank()) {
            sb.append(contact).append("\n");
        }
        sb.append("\n");

        // ── Summary ──────────────────────────────────────────────────────
        if (hasContent(r.getSummary())) {
            sb.append("PROFESSIONAL SUMMARY\n");
            sb.append(dash(20)).append("\n");
            sb.append(safe(r.getSummary())).append("\n\n");
        }

        // ── Skills ───────────────────────────────────────────────────────
        List<String> skills = jsonUtil.toStringList(r.getSkillsJson());
        if (!skills.isEmpty()) {
            sb.append("SKILLS\n");
            sb.append(dash(20)).append("\n");
            sb.append(String.join(", ", skills)).append("\n\n");
        }

        // ── Experience ───────────────────────────────────────────────────
        if (!r.getExperiences().isEmpty()) {
            sb.append("EXPERIENCE\n");
            sb.append(dash(20)).append("\n");
            for (Experience e : r.getExperiences()) {
                sb.append(safe(e.getRole()));
                if (hasContent(e.getCompany())) sb.append(" | ").append(safe(e.getCompany()));
                sb.append("\n");

                String meta = joinPipe(joinDateRange(e.getStartDate(), e.getEndDate()), e.getLocation());
                if (!meta.isBlank()) sb.append(meta).append("\n");

                List<String> bullets = jsonUtil.toStringList(e.getBulletsJson());
                for (String b : bullets) {
                    if (b != null && !b.isBlank()) sb.append("• ").append(b.trim()).append("\n");
                }
                sb.append("\n");
            }
        }

        // ── Projects ─────────────────────────────────────────────────────
        if (!r.getProjects().isEmpty()) {
            sb.append("PROJECTS\n");
            sb.append(dash(20)).append("\n");
            for (Project p : r.getProjects()) {
                sb.append(safe(p.getName()));
                if (hasContent(p.getLink())) sb.append(" | ").append(safe(p.getLink()));
                sb.append("\n");
                if (hasContent(p.getDescription())) sb.append(safe(p.getDescription())).append("\n");
                sb.append("\n");
            }
        }

        // ── Education ────────────────────────────────────────────────────
        if (!r.getEducationEntries().isEmpty()) {
            sb.append("EDUCATION\n");
            sb.append(dash(20)).append("\n");
            for (Education e : r.getEducationEntries()) {
                String degree = joinSpaces(e.getDegree(), e.getField() != null ? "in " + e.getField() : null);
                sb.append(degree);
                if (hasContent(e.getInstitution())) sb.append(" | ").append(safe(e.getInstitution()));
                sb.append("\n");
                String dates = joinDateRange(e.getStartDate(), e.getEndDate());
                if (!dates.isBlank()) sb.append(dates).append("\n");
                sb.append("\n");
            }
        }

        // ── Certifications ───────────────────────────────────────────────
        List<String> certs = jsonUtil.toStringList(r.getCertificationsJson());
        if (!certs.isEmpty()) {
            sb.append("CERTIFICATIONS\n");
            sb.append(dash(20)).append("\n");
            certs.forEach(c -> sb.append("• ").append(c).append("\n"));
            sb.append("\n");
        }

        // ── Achievements ─────────────────────────────────────────────────
        List<String> achievements = jsonUtil.toStringList(r.getAchievementsJson());
        if (!achievements.isEmpty()) {
            sb.append("ACHIEVEMENTS\n");
            sb.append(dash(20)).append("\n");
            achievements.forEach(a -> sb.append("• ").append(a).append("\n"));
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String buildFilename(Long resumeId, User user) {
        Resume r = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        String name = safe(r.getFullName())
                .replaceAll("[^a-zA-Z0-9\\s-]", "").trim()
                .replaceAll("\\s+", "_").toLowerCase();
        return (name.isBlank() ? "resume" : name) + "_resume.txt";
    }

    private String safe(String v)            { return v == null ? "" : v.trim(); }
    private boolean hasContent(String v)     { return v != null && !v.trim().isBlank(); }
    private String dash(int n)               { return "-".repeat(n); }

    private String joinPipe(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) sb.append(" | ");
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private String joinSpaces(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private String joinDateRange(String start, String end) {
        if (start == null && end == null) return "";
        if (end == null) return safe(start);
        if (start == null) return safe(end);
        return safe(start) + " - " + safe(end);
    }
}
