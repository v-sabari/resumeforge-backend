package com.resumeforge.ai.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-document parsing tests for {@link DocumentParsingService}.
 *
 * <p>These tests exist primarily as a regression guard for the root cause of
 * the FlowCV "no readable text" bug: the inner {@code PositionCollector}
 * extended {@code PDFTextStripper} and overrode {@code writeString(String,
 * List)} without calling {@code super.writeString(...)}, which suppressed the
 * extractor's output entirely — so <em>every</em> PDF produced empty text. They
 * therefore build genuine PDFs/DOCX (via PDFBox/POI) and assert on the actual
 * extracted text instead of mocking it.</p>
 */
class DocumentParsingServiceTest {

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Test
    void standardTextPdfExtractsItsText() throws Exception {
        byte[] pdf = TestDocuments.singlePagePdf(
                "John Doe", "john.doe@example.com", "SKILLS", "Java, Docker, SQL");

        ParsedDocument doc = new DocumentParsingService().parse(pdf, "resume.pdf", "application/pdf");

        assertThat(doc.type()).isEqualTo("PDF");
        assertThat(doc.pageCount()).isEqualTo(1);
        assertThat(doc.hasUsableText()).isTrue();
        assertThat(doc.text()).contains("John Doe", "SKILLS", "Java, Docker, SQL");
        assertThat(doc.wordCount()).isGreaterThanOrEqualTo(5);
    }

    /**
     * Mimics a FlowCV-style export: a dense, single-page resume with a name,
     * contact line, section headings and several entries. The original bug made
     * this (and every other PDF) return zero text.
     */
    @Test
    void flowCvStyleExportExtractsAllSections() throws IOException {
        byte[] pdf = TestDocuments.singlePagePdf(
                "Jordan Rivera",
                "jordan.rivera@example.com | +1 555 123 4567 | linkedin.com/in/jordanrivera",
                "Professional Summary",
                "Backend engineer with 6 years building distributed systems in Java and Spring Boot.",
                "Skills",
                "Java, Spring Boot, PostgreSQL, Docker, Kubernetes, AWS",
                "Experience",
                "Senior Software Engineer, Acme Corp, 2021-Present",
                "Reduced API latency by 40% by introducing caching and query optimisation.",
                "Education",
                "B.Sc. Computer Science, Example University, 2013-2017");

        ParsedDocument doc = new DocumentParsingService().parse(pdf, "Jordan_Rivera_Resume.pdf", "application/pdf");

        assertThat(doc.hasUsableText()).isTrue();
        assertThat(doc.text()).contains(
                "Jordan Rivera",
                "jordan.rivera@example.com",
                "Professional Summary",
                "Skills",
                "Java, Spring Boot, PostgreSQL, Docker, Kubernetes, AWS",
                "Experience",
                "Education");
        assertThat(doc.text()).doesNotContain("\uFFFD");
    }

    @Test
    void multiPagePdfExtractsEveryPage() throws IOException {
        byte[] pdf = TestDocuments.pagesPdf(List.of(
                List.of("Page One Header", "Experience part one"),
                List.of("Page Two Header", "Education continues here")));

        ParsedDocument doc = parsing().parse(pdf, "resume.pdf", "application/pdf");

        assertThat(doc.pageCount()).isEqualTo(2);
        assertThat(doc.text()).contains(
                "Page One Header", "Experience part one",
                "Page Two Header", "Education continues here");
    }

    @Test
    void unicodeAndSpecialPunctuationAreExtracted() throws IOException {
        byte[] pdf = TestDocuments.unicodePdf("Résumé – José Müller • Zürich — €50 & CAFÉ");

        ParsedDocument doc = parsing().parse(pdf, "cv.pdf", "application/pdf");

        assertThat(doc.hasUsableText()).isTrue();
        assertThat(doc.text()).contains("Résumé", "José", "Müller", "Zürich", "€50", "CAFÉ");
    }

    @Test
    void validPdfWithNoTextHasNoUsableText() throws IOException {
        ParsedDocument doc = parsing().parse(TestDocuments.emptyPdf(), "blank.pdf", "application/pdf");

        assertThat(doc.type()).isEqualTo("PDF");
        assertThat(doc.hasUsableText()).isFalse();
        assertThat(doc.charCount()).isZero();
        assertThat(doc.wordCount()).isZero();
    }

    @Test
    void corruptedPdfIsRejectedWithAClearMessage() {
        assertThatThrownBy(() ->
                parsing().parse(TestDocuments.corruptedPdf(), "resume.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corrupted");
    }

    @Test
    void passwordProtectedPdfIsRejectedWithAClearMessage() throws IOException {
        byte[] encrypted = TestDocuments.encryptedPdf("user-pass");

        assertThatThrownBy(() ->
                parsing().parse(encrypted, "locked.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password-protected");
    }

    // ── DOCX / TXT / unsupported ──────────────────────────────────────────────

    @Test
    void docxExtractsParagraphText() throws IOException {
        byte[] docx = TestDocuments.docx(
                "John Doe", "john@example.com", "Skills", "Java, Docker, SQL",
                "Experience", "Engineer at Acme");

        ParsedDocument doc = parsing().parse(docx, "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(doc.type()).isEqualTo("DOCX");
        assertThat(doc.hasUsableText()).isTrue();
        assertThat(doc.text()).contains("John Doe", "Skills", "Java, Docker, SQL", "Experience");
    }

    @Test
    void txtExtractsText() {
        byte[] txt = "John Doe\nSkills\nJava, Docker, SQL".getBytes(StandardCharsets.UTF_8);

        ParsedDocument doc = parsing().parse(txt, "resume.txt", "text/plain");

        assertThat(doc.type()).isEqualTo("TXT");
        assertThat(doc.hasUsableText()).isTrue();
        assertThat(doc.text()).contains("John Doe", "Java, Docker, SQL");
    }

    @Test
    void legacyDocFormatIsRejectedAsUnsupported() {
        assertThatThrownBy(() ->
                parsing().parse(new byte[]{1, 2, 3, 4}, "resume.doc", "application/msword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".doc");
    }

    @Test
    void emptyUploadIsRejected() {
        assertThatThrownBy(() -> parsing().parse(new byte[0], "resume.pdf", "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    private DocumentParsingService parsing() {
        return new DocumentParsingService();
    }
}
