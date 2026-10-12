package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TOOL-02: response for the free ATS Format &amp; Parsing Checker
 * ({@code POST /api/public/tools/format-check}).
 *
 * Findings are explicitly split into {@code CONFIRMED} problems (directly
 * detected by the parser) and {@code RISK} items (potential issues that cannot
 * be proven with the available parsing library). The readiness {@code score} is
 * a heuristic estimate, NOT a guarantee that every ATS will parse the resume.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormatCheckResponse {

    /** Format-readiness / parsing-quality estimate, 0-100. */
    private int score;

    private String scoreLabel;

    private String fileName;

    private String fileType;

    /** True when readable text was extracted from the document. */
    private boolean textExtracted;

    private int pageCount;

    private int wordCount;

    private int charCount;

    /** Confirmed parsing problems (severity = CONFIRMED). */
    private List<Finding> problems;

    /** Potential formatting risks (severity = RISK). */
    private List<Finding> risks;

    /** Detected resume sections. */
    private List<SectionStatus> detectedSections;

    /** Expected-but-not-detected sections. */
    private List<String> missingSections;

    /** Actionable recommendations. */
    private List<String> recommendations;

    /** What the parser could actually read (PII lightly masked, truncated). */
    private String textPreview;

    /** Raw, machine-readable parse signals so the UI can explain findings. */
    private ParseSignals signals;

    private String disclaimer;

    // ── Nested types ──────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding {
        /** CONFIRMED | RISK */
        private String severity;
        /** e.g. "Text extraction", "Layout", "Contact info", "Sections" */
        private String category;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionStatus {
        private String name;
        /** Was a matching heading found? */
        private boolean found;
        /** The heading text that was matched, if any. */
        private String heading;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParseSignals {
        private boolean hasEmail;
        private boolean hasPhone;
        private boolean hasLinkedin;
        private boolean hasNameLikeLine;
        private int imageCount;
        private int tableCount;
        /** Multi-column layout suspected from text positions. */
        private boolean multiColumnSuspected;
        private boolean headerFooterTextPresent;
        /** Percentage of extracted characters that look garbled (0-100). */
        private int garbledPercent;
        private int headingCount;
        /** Words per page — unusually low can mean scanned/image-heavy. */
        private int wordsPerPage;
    }
}
