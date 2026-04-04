package com.cvcraft.ai.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.cvcraft.ai.entity.*;
import com.cvcraft.ai.exception.ResourceNotFoundException;
import com.cvcraft.ai.repository.ResumeRepository;
import com.cvcraft.ai.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PdfExportService {

    private final ResumeRepository resumeRepository;
    private final JsonUtil         jsonUtil;

    public PdfExportService(ResumeRepository resumeRepository, JsonUtil jsonUtil) {
        this.resumeRepository = resumeRepository;
        this.jsonUtil         = jsonUtil;
    }

    // ── Public API ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateResumePdf(Long resumeId, User user) {
        // BUG FIX: Was previously using a hardcoded default — now ALWAYS loads by resumeId + user
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume not found or does not belong to this user."));
        return buildPdf(resume);
    }

    @Transactional(readOnly = true)
    public String buildFilename(Long resumeId, User user) {
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        String base = safe(resume.getFullName()).trim();
        if (base.isBlank()) base = "resume";
        String sanitized = base.replaceAll("[^a-zA-Z0-9\\-\\s_]", "")
                               .trim().replaceAll("\\s+", "_");
        if (sanitized.isBlank()) sanitized = "resume";
        return sanitized + ".pdf";
    }

    // ── PDF Builder ─────────────────────────────────────────────────

    private byte[] buildPdf(Resume r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 45, 45);
            PdfWriter.getInstance(doc, out);
            doc.open();
            writeClassicTemplate(doc, r);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export resume PDF", e);
        }
    }

    // ── Classic (ATS-safe single column) ────────────────────────────

    private void writeClassicTemplate(Document doc, Resume r) throws DocumentException {
        Color brandBlue  = new Color(0, 93, 212);
        Color darkGray   = new Color(30, 41, 59);
        Color medGray    = new Color(71, 85, 105);
        Color lightGray  = new Color(203, 213, 225);

        Font nameFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   18, darkGray);
        Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA,        11, medGray);
        Font contactFont = FontFactory.getFont(FontFactory.HELVETICA,         9, medGray);
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   10, brandBlue);
        Font bodyFont    = FontFactory.getFont(FontFactory.HELVETICA,        10, darkGray);
        Font boldBody    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   10, darkGray);
        Font metaFont    = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, medGray);
        Font bulletFont  = FontFactory.getFont(FontFactory.HELVETICA,         9, darkGray);

        // ── Header ──────────────────────────────────────────────────
        Paragraph namePara = new Paragraph(safe(r.getFullName(), "Your Name"), nameFont);
        namePara.setAlignment(Element.ALIGN_LEFT);
        namePara.setSpacingAfter(3f);
        doc.add(namePara);

        if (!safe(r.getRole()).isBlank()) {
            Paragraph rolePara = new Paragraph(safe(r.getRole()), titleFont);
            rolePara.setSpacingAfter(5f);
            doc.add(rolePara);
        }

        // Contact line
        String contact = joinNonBlank("  •  ", r.getEmail(), r.getPhone(),
                r.getLocation(), r.getLinkedin(), r.getGithub(), r.getPortfolio());
        if (!contact.isBlank()) {
            Paragraph cp = new Paragraph(contact, contactFont);
            cp.setSpacingAfter(10f);
            doc.add(cp);
        }

        addHRule(doc, brandBlue);

        // ── Summary ──────────────────────────────────────────────────
        if (!safe(r.getSummary()).isBlank()) {
            addSection(doc, "PROFESSIONAL SUMMARY", headingFont, lightGray);
            addBody(doc, r.getSummary(), bodyFont, 10f);
        }

        // ── Skills ───────────────────────────────────────────────────
        List<String> skills = jsonUtil.toStringList(r.getSkillsJson());
        if (!skills.isEmpty()) {
            addSection(doc, "SKILLS", headingFont, lightGray);
            addBody(doc, String.join("  ·  ", skills), bodyFont, 10f);
        }

        // ── Experience ───────────────────────────────────────────────
        if (!r.getExperiences().isEmpty()) {
            addSection(doc, "EXPERIENCE", headingFont, lightGray);
            for (Experience exp : r.getExperiences()) {
                // Role — Company
                String expTitle = joinNonBlank(" — ", exp.getRole(), exp.getCompany());
                Paragraph tp = new Paragraph(expTitle, boldBody);
                tp.setSpacingAfter(1f);
                doc.add(tp);
                // Meta: location | dates
                String dates = joinNonBlank(" – ", exp.getStartDate(), exp.getEndDate());
                String meta  = joinNonBlank("  |  ", exp.getLocation(), dates);
                if (!meta.isBlank()) {
                    Paragraph mp = new Paragraph(meta, metaFont);
                    mp.setSpacingAfter(3f);
                    doc.add(mp);
                }
                // Bullets
                List<String> bullets = jsonUtil.toStringList(exp.getBulletsJson());
                for (String b : bullets) {
                    if (b == null || b.isBlank()) continue;
                    Paragraph bp = new Paragraph("•  " + b.trim(), bulletFont);
                    bp.setIndentationLeft(14f);
                    bp.setSpacingAfter(2f);
                    doc.add(bp);
                }
                addSpacer(doc, 6f);
            }
        }

        // ── Projects ─────────────────────────────────────────────────
        if (!r.getProjects().isEmpty()) {
            addSection(doc, "PROJECTS", headingFont, lightGray);
            for (Project p : r.getProjects()) {
                Paragraph pp = new Paragraph(safe(p.getName()), boldBody);
                pp.setSpacingAfter(1f);
                doc.add(pp);
                if (!safe(p.getLink()).isBlank()) {
                    Paragraph lp = new Paragraph(p.getLink(), metaFont);
                    lp.setSpacingAfter(2f);
                    doc.add(lp);
                }
                if (!safe(p.getDescription()).isBlank()) {
                    Paragraph dp = new Paragraph(p.getDescription(), bulletFont);
                    dp.setIndentationLeft(0f);
                    dp.setSpacingAfter(6f);
                    doc.add(dp);
                }
            }
        }

        // ── Education ────────────────────────────────────────────────
        if (!r.getEducationEntries().isEmpty()) {
            addSection(doc, "EDUCATION", headingFont, lightGray);
            for (Education e : r.getEducationEntries()) {
                String degTitle = joinNonBlank(" — ", e.getDegree(), e.getInstitution());
                Paragraph dp = new Paragraph(degTitle, boldBody);
                dp.setSpacingAfter(1f);
                doc.add(dp);
                String dates = joinNonBlank(" – ", e.getStartDate(), e.getEndDate());
                String meta  = joinNonBlank("  |  ", safe(e.getField()), dates);
                if (!meta.isBlank()) {
                    Paragraph mp = new Paragraph(meta, metaFont);
                    mp.setSpacingAfter(6f);
                    doc.add(mp);
                }
            }
        }

        // ── Certifications ───────────────────────────────────────────
        List<String> certs = jsonUtil.toStringList(r.getCertificationsJson());
        if (!certs.isEmpty()) {
            addSection(doc, "CERTIFICATIONS", headingFont, lightGray);
            for (String c : certs) {
                if (c == null || c.isBlank()) continue;
                Paragraph cp = new Paragraph("•  " + c.trim(), bulletFont);
                cp.setIndentationLeft(14f);
                cp.setSpacingAfter(2f);
                doc.add(cp);
            }
            addSpacer(doc, 6f);
        }

        // ── Achievements ─────────────────────────────────────────────
        List<String> achievements = jsonUtil.toStringList(r.getAchievementsJson());
        if (!achievements.isEmpty()) {
            addSection(doc, "ACHIEVEMENTS", headingFont, lightGray);
            for (String a : achievements) {
                if (a == null || a.isBlank()) continue;
                Paragraph ap = new Paragraph("•  " + a.trim(), bulletFont);
                ap.setIndentationLeft(14f);
                ap.setSpacingAfter(2f);
                doc.add(ap);
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private void addSection(Document doc, String title, Font font, Color ruleColor) throws DocumentException {
        addSpacer(doc, 4f);
        Paragraph p = new Paragraph(title, font);
        p.setSpacingAfter(2f);
        doc.add(p);
        addHRule(doc, ruleColor);
        addSpacer(doc, 3f);
    }

    private void addHRule(Document doc, Color color) throws DocumentException {
        LineSeparator ls = new LineSeparator();
        ls.setLineColor(color);
        ls.setLineWidth(0.5f);
        doc.add(new Chunk(ls));
        addSpacer(doc, 3f);
    }

    private void addBody(Document doc, String text, Font font, float spacingAfter) throws DocumentException {
        if (text == null || text.isBlank()) return;
        Paragraph p = new Paragraph(safe(text), font);
        p.setSpacingAfter(spacingAfter);
        p.setLeading(14f);
        doc.add(p);
    }

    private void addSpacer(Document doc, float spacing) throws DocumentException {
        Paragraph sp = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 2f));
        sp.setSpacingAfter(spacing);
        doc.add(sp);
    }

    private String safe(String value) {
        if (value == null) return "";
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).trim();
    }

    private String safe(String value, String fallback) {
        String s = safe(value);
        return s.isBlank() ? fallback : s;
    }

    private String joinNonBlank(String delimiter, String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                if (!sb.isEmpty()) sb.append(delimiter);
                sb.append(v.trim());
            }
        }
        return sb.toString();
    }
}
