package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.FormatCheckResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * TOOL-02: deterministic ATS Format &amp; Parsing Checker.
 *
 * <p>Runs entirely on the parsed document; no AI, no mocking. Every finding is
 * derived from a signal the parser can actually observe and is classified as
 * either {@code CONFIRMED} (directly detected) or {@code RISK} (a potential
 * problem that cannot be proven with the available library). Nothing is
 * reported as a confirmed error without evidence.</p>
 *
 * <p>The produced {@code score} is a heuristic format-readiness estimate and is
 * explicitly presented as such — it is not a guarantee that any ATS will parse
 * the resume.</p>
 */
@Service
public class FormatChecker {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+?\\d[\\d\\s().-]{7,}\\d)");
    private static final Pattern LINKEDIN = Pattern.compile(
            "(?i)linkedin\\.com|linkedin");

    private static final int PREVIEW_CHARS = 1500;

    public FormatCheckResponse check(ParsedDocument doc, String filename) {
        String text = doc.text() == null ? "" : doc.text();
        boolean textExtracted = doc.hasUsableText() && doc.charCount() >= 50;
        boolean headerFooter = doc.headerFooterText() != null && !doc.headerFooterText().isBlank();
        int wordsPerPage = doc.pageCount() > 0
                ? Math.round((float) doc.wordCount() / doc.pageCount())
                : doc.wordCount();

        List<FormatCheckResponse.Finding> problems = new ArrayList<>();
        List<FormatCheckResponse.Finding> risks = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // ── Confirmed findings (directly detected) ────────────────────────────
        if (!textExtracted) {
            problems.add(finding("CONFIRMED", "Text extraction",
                    "Nearly no machine-readable text could be extracted from this file. "
                            + "Most ATS parsers will fail to read it."));
            recommendations.add("Export your resume as a text-based PDF or DOCX instead of a scan or image.");
        } else if (doc.garbledPercent() > 8) {
            problems.add(finding("CONFIRMED", "Text extraction",
                    "About " + doc.garbledPercent() + "% of the extracted characters look garbled or "
                            + "unreadable, which usually indicates a font/encoding or scanned-image problem."));
            recommendations.add("Re-export the resume to a PDF/DOCX with embedded, standard fonts.");
        }

        boolean hasEmail = EMAIL.matcher(text).find();
        boolean hasPhone = PHONE.matcher(text).find();
        boolean hasLinkedin = LINKEDIN.matcher(text).find();

        if (textExtracted && !hasEmail) {
            problems.add(finding("CONFIRMED", "Contact info",
                    "No email address was detected in the extracted text."));
            recommendations.add("Add a plain-text email address near the top of the resume.");
        }
        if (textExtracted && !hasPhone) {
            risks.add(finding("RISK", "Contact info",
                    "No phone number was detected. If one exists, it may be inside an image, text box, "
                            + "header or footer that parsers skip."));
        }

        // ── Potential risks (only when the parser can actually observe them) ──
        if (doc.tableCount() > 0) {
            risks.add(finding("RISK", "Layout",
                    "This document contains " + doc.tableCount() + " table(s). Some ATS parsers read "
                            + "table cells in the wrong order or drop them entirely."));
            recommendations.add("Replace tables with simple single-column text and bullet points.");
        }
        if (doc.multiColumnSuspected()) {
            risks.add(finding("RISK", "Layout",
                    "A multi-column layout was detected. Multi-column resumes are a common cause of "
                            + "out-of-order or merged text in ATS parsers."));
            recommendations.add("Use a single-column layout so text is read top-to-bottom.");
        }
        if (headerFooter) {
            risks.add(finding("RISK", "Layout",
                    "Content was found in the document header/footer. Many ATS parsers ignore "
                            + "headers and footers entirely."));
            recommendations.add("Move important details (name, contact, links) out of headers/footers into the body.");
        }
        if (doc.imageCount() > 0) {
            if (textExtracted && wordsPerPage < 50) {
                risks.add(finding("RISK", "Layout",
                        "The file contains " + doc.imageCount() + " image(s) and very little extractable text. "
                                + "Important information may be locked inside those images."));
            } else {
                risks.add(finding("RISK", "Layout",
                        "The document contains " + doc.imageCount() + " embedded image(s). Text inside images "
                                + "or graphics is invisible to most ATS parsers."));
            }
            recommendations.add("Avoid placing contact details, skills or experience inside images or graphics.");
        }

        boolean scannedSuspicion = textExtracted && doc.imageCount() > 0 && wordsPerPage < 50;
        if (scannedSuspicion) {
            risks.add(finding("RISK", "Scanned document",
                    "Only about " + wordsPerPage + " words per page were extracted while images are present. "
                            + "This file may be a scan rather than real, selectable text."));
        }

        if (textExtracted && doc.wordCount() < 150) {
            risks.add(finding("RISK", "Content length",
                    "Only " + doc.wordCount() + " words were found — the resume may be too short "
                            + "for an ATS to extract meaningful detail."));
            recommendations.add("Expand the resume to roughly 400–700 words with concrete achievements.");
        } else if (doc.wordCount() > 1200) {
            risks.add(finding("RISK", "Content length",
                    "The resume is long (" + doc.wordCount() + " words). Consider trimming to 1–2 pages."));
        }

        // ── Section detection ─────────────────────────────────────────────────
        List<FormatCheckResponse.SectionStatus> detected = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        boolean summary = detect(text, "summary", "professional summary", "objective", "profile", "about");
        boolean experience = detect(text, "experience", "work experience", "professional experience",
                "employment", "work history");
        boolean education = detect(text, "education", "academic", "academics", "qualifications");
        boolean skills = detect(text, "skills", "technical skills", "core competencies", "competencies");
        boolean projects = detect(text, "projects", "personal projects", "academic projects");
        boolean certifications = detect(text, "certifications", "certificates", "certification");
        boolean achievements = detect(text, "achievements", "accomplishments", "awards");

        addSection(detected, missing, "Summary", summary, false);
        addSection(detected, missing, "Experience", experience, true);
        addSection(detected, missing, "Education", education, true);
        addSection(detected, missing, "Skills", skills, true);
        addSection(detected, missing, "Projects", projects, false);
        addSection(detected, missing, "Certifications", certifications, false);
        addSection(detected, missing, "Achievements", achievements, false);

        if (textExtracted && (!experience || !education || !skills)) {
            risks.add(finding("RISK", "Sections",
                    "One or more standard sections (Experience, Education, Skills) were not detected. "
                            + "ATS parsers rely on standard headings to categorise content."));
            recommendations.add("Use standard section headings: Summary, Skills, Experience, Education.");
        }

        // ── Honest limitation: structural detection is heuristic ──────────────
        risks.add(finding("RISK", "Layout",
                "Tables, columns, text boxes and header/footer content can only be detected heuristically. "
                        + "Some layout issues may not be visible to this checker."));

        int score = computeScore(doc, textExtracted, hasEmail, headerFooter,
                experience, education, skills, wordsPerPage, scannedSuspicion);

        FormatCheckResponse.ParseSignals signals = FormatCheckResponse.ParseSignals.builder()
                .hasEmail(hasEmail)
                .hasPhone(hasPhone)
                .hasLinkedin(hasLinkedin)
                .hasNameLikeLine(nameLikeLine(text))
                .imageCount(doc.imageCount())
                .tableCount(doc.tableCount())
                .multiColumnSuspected(doc.multiColumnSuspected())
                .headerFooterTextPresent(headerFooter)
                .garbledPercent(doc.garbledPercent())
                .headingCount(countDetected(detected))
                .wordsPerPage(wordsPerPage)
                .build();

        return FormatCheckResponse.builder()
                .score(score)
                .scoreLabel(scoreLabel(score))
                .fileName(filename)
                .fileType(doc.type())
                .textExtracted(textExtracted)
                .pageCount(doc.pageCount())
                .wordCount(doc.wordCount())
                .charCount(doc.charCount())
                .problems(problems)
                .risks(risks)
                .detectedSections(detected)
                .missingSections(missing)
                .recommendations(recommendations)
                .textPreview(preview(text))
                .signals(signals)
                .disclaimer("This is an estimated format-readiness assessment based on what a standard "
                        + "parser can extract. A high score is not a guarantee that every applicant "
                        + "tracking system will parse your resume correctly.")
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Lightweight format-quality score (0-100) used as the {@code formatQuality}
     * factor of the JD-Based ATS Checker. Shares the exact same deterministic
     * formula as {@link #check}, without building the full response object.
     */
    public int readinessScore(ParsedDocument doc) {
        String text = doc.text() == null ? "" : doc.text();
        boolean textExtracted = doc.hasUsableText() && doc.charCount() >= 50;
        boolean headerFooter = doc.headerFooterText() != null && !doc.headerFooterText().isBlank();
        int wordsPerPage = doc.pageCount() > 0
                ? Math.round((float) doc.wordCount() / doc.pageCount())
                : doc.wordCount();
        boolean hasEmail = EMAIL.matcher(text).find();
        boolean experience = detect(text, "experience", "work experience", "professional experience",
                "employment", "work history");
        boolean education = detect(text, "education", "academic", "academics", "qualifications");
        boolean skills = detect(text, "skills", "technical skills", "core competencies", "competencies");
        boolean scannedSuspicion = textExtracted && doc.imageCount() > 0 && wordsPerPage < 50;
        return computeScore(doc, textExtracted, hasEmail, headerFooter,
                experience, education, skills, wordsPerPage, scannedSuspicion);
    }

    private FormatCheckResponse.Finding finding(String severity, String category, String message) {
        return FormatCheckResponse.Finding.builder()
                .severity(severity).category(category).message(message).build();
    }

    private void addSection(List<FormatCheckResponse.SectionStatus> detected,
                            List<String> missing, String name, boolean found, boolean core) {
        if (found) {
            detected.add(FormatCheckResponse.SectionStatus.builder()
                    .name(name).found(true).heading(name).build());
        } else if (core) {
            missing.add(name);
        }
    }

    private int countDetected(List<FormatCheckResponse.SectionStatus> detected) {
        return (int) detected.stream().filter(FormatCheckResponse.SectionStatus::isFound).count();
    }

    /** Detects a heading by looking for short lines containing one of the aliases. */
    private boolean detect(String text, String... aliases) {
        if (text == null || text.isBlank()) return false;
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.length() > 40) continue;
            String norm = AtsKeywordAnalyzer.normalize(line);
            for (String alias : aliases) {
                String a = AtsKeywordAnalyzer.normalize(alias);
                if (!a.isEmpty() && AtsKeywordAnalyzer.indexOfPhrase(norm, a) >= 0) return true;
            }
        }
        return false;
    }

    /** Best-effort: does a short leading line look like a person's name? */
    private boolean nameLikeLine(String text) {
        if (text == null || text.isBlank()) return false;
        int checked = 0;
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (++checked > 5) break;
            if (line.length() < 3 || line.length() > 50) continue;
            if (line.matches(".*\\d.*")) continue;
            if (EMAIL.matcher(line).find() || LINKEDIN.matcher(line).find()) continue;
            String[] words = line.split("\\s+");
            if (words.length < 2 || words.length > 4) continue;
            boolean allNameLike = true;
            for (String w : words) {
                if (w.length() < 2 || !Character.isUpperCase(w.charAt(0))) {
                    allNameLike = false;
                    break;
                }
            }
            if (allNameLike) return true;
        }
        return false;
    }

    private int computeScore(ParsedDocument doc, boolean textExtracted, boolean hasEmail,
                             boolean headerFooter, boolean experience, boolean education,
                             boolean skills, int wordsPerPage, boolean scannedSuspicion) {
        if (!textExtracted) return 8;

        int score = 100;
        if (doc.garbledPercent() > 8) score -= 30;
        else if (doc.garbledPercent() > 3) score -= 12;

        if (doc.tableCount() > 0) score -= 10;
        if (doc.multiColumnSuspected()) score -= 12;
        if (headerFooter) score -= 8;
        if (!hasEmail) score -= 10;
        if (scannedSuspicion) score -= 20;

        if (!experience) score -= 10;
        if (!education) score -= 8;
        if (!skills) score -= 8;

        if (doc.wordCount() < 150) score -= 10;
        if (doc.wordCount() > 1200) score -= 5;

        return Math.max(0, Math.min(100, score));
    }

    private String scoreLabel(int score) {
        if (score >= 85) return "ATS-friendly";
        if (score >= 70) return "Mostly parseable";
        if (score >= 50) return "Needs cleanup";
        return "High parsing risk";
    }

    /** Masks obvious PII before returning a truncated preview to the client. */
    private String preview(String text) {
        if (text == null || text.isBlank()) return "";
        String masked = EMAIL.matcher(text).replaceAll("[email hidden]");
        masked = PHONE.matcher(masked).replaceAll("[phone hidden]");
        masked = masked.replaceAll("\\s{2,}", " ").trim();
        if (masked.length() > PREVIEW_CHARS) {
            masked = masked.substring(0, PREVIEW_CHARS) + " …";
        }
        return masked;
    }
}
