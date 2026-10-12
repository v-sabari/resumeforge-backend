package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.FormatCheckResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOOL-02: deterministic unit tests for the ATS Format &amp; Parsing Checker.
 * ParsedDocument signals are supplied directly so no real file is needed.
 */
class FormatCheckerTest {

    private final FormatChecker checker = new FormatChecker();

    private static final String BASE = "John Doe\n"
            + "john.doe@example.com | +1 555 123 4567\n"
            + "Summary\n"
            + "Experienced software engineer focused on reliable backend systems and clean APIs.\n"
            + "Skills\n"
            + "Java, Spring, Docker, SQL, AWS\n"
            + "Experience\n"
            + "Software Engineer, Acme Corp, 2020-2023. Built and maintained services.\n"
            + "Education\n"
            + "B.Tech in Computer Science, Example University, 2016-2020.\n";

    private ParsedDocument doc(String text) {
        return doc(text, 1, 0, 0, false, "", 0);
    }

    private ParsedDocument doc(String text, int pages, int images, int tables,
                               boolean multiColumn, String headerFooter, int garbled) {
        int chars = text.length();
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        return new ParsedDocument(text, "PDF", pages, chars, words,
                images, tables, multiColumn, headerFooter, garbled);
    }

    private boolean hasProblem(FormatCheckResponse r, String category) {
        return r.getProblems().stream().anyMatch(f -> category.equals(f.getCategory()));
    }

    private boolean hasRiskMentioning(FormatCheckResponse r, String needle) {
        return r.getRisks().stream().anyMatch(f -> f.getMessage().toLowerCase().contains(needle));
    }

    @Test
    void cleanResumeHasExtractedTextNoConfirmedProblemsAndDetectsSections() {
        FormatCheckResponse r = checker.check(doc(BASE), "resume.pdf");

        assertThat(r.isTextExtracted()).isTrue();
        assertThat(r.getProblems()).isEmpty();
        assertThat(r.getScore()).isBetween(0, 100);

        assertThat(r.getDetectedSections()).extracting(FormatCheckResponse.SectionStatus::getName)
                .contains("Experience", "Education", "Skills");
        assertThat(r.getMissingSections()).doesNotContain("Experience", "Education", "Skills");
    }

    @Test
    void missingEmailIsAConfirmedProblemAndLowersTheScore() {
        FormatCheckResponse withEmail = checker.check(doc(BASE), "resume.pdf");
        String noEmail = BASE.replace("john.doe@example.com | ", "");
        FormatCheckResponse without = checker.check(doc(noEmail), "resume.pdf");

        assertThat(hasProblem(without, "Contact info")).isTrue();
        assertThat(without.getScore()).isLessThan(withEmail.getScore());
    }

    @Test
    void highGarbledTextIsAConfirmedProblem() {
        FormatCheckResponse r = checker.check(doc(BASE, 1, 0, 0, false, "", 25), "resume.pdf");
        assertThat(hasProblem(r, "Text extraction")).isTrue();
    }

    @Test
    void tablesAndColumnsAreReportedAsRisks() {
        FormatCheckResponse r = checker.check(doc(BASE, 1, 0, 2, true, "Jane Doe", 0), "resume.pdf");

        assertThat(hasRiskMentioning(r, "table")).isTrue();
        assertThat(hasRiskMentioning(r, "column")).isTrue();
        assertThat(hasRiskMentioning(r, "header")).isTrue();
    }

    @Test
    void imageHeavyDocumentIsFlaggedAsPossibleScan() {
        // 1 page, several images, almost no extractable words.
        FormatCheckResponse r = checker.check(doc("Scan", 1, 3, 0, false, "", 0), "resume.pdf");

        assertThat(r.isTextExtracted()).isFalse();
        assertThat(hasProblem(r, "Text extraction")).isTrue();
    }

    @Test
    void readinessScoreMatchesFullCheckScore() {
        ParsedDocument d = doc(BASE, 1, 1, 1, true, "Jane", 2);
        assertThat(checker.readinessScore(d)).isEqualTo(checker.check(d, "resume.pdf").getScore());
    }

    @Test
    void emptyDocumentScoresVeryLow() {
        FormatCheckResponse r = checker.check(doc(""), "resume.pdf");
        assertThat(r.isTextExtracted()).isFalse();
        assertThat(r.getScore()).isLessThan(20);
    }
}
