package com.resumeforge.ai.service;

/**
 * TOOL-01 / TOOL-02: the raw result of parsing an uploaded resume file.
 *
 * <p>This is an internal model (not serialized directly). It carries both the
 * extracted text and the structural signals the Format &amp; Parsing Checker
 * needs. Structural fields are only populated when the underlying parser can
 * actually observe them — {@code -1} / {@code false} means "not detectable",
 * never a guess.</p>
 */
public record ParsedDocument(
        String text,
        String type,
        int pageCount,
        int charCount,
        int wordCount,
        int imageCount,
        int tableCount,
        boolean multiColumnSuspected,
        String headerFooterText,
        int garbledPercent
) {
    public boolean hasUsableText() {
        return text != null && !text.isBlank();
    }
}
