package com.resumeforge.ai.service;

import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.ExportHistoryRepository;
import com.resumeforge.ai.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for "Summary text wrapping / paragraph preservation".
 *
 * <p>A summary entered in the editor can contain blank-line separated
 * paragraphs. Before the fix, {@code ExportService.dBody} wrote the whole value
 * into a single DOCX run, so Word collapsed every {@code \n} and the paragraphs
 * ran together. The TXT renderer already appended the raw value, so it is
 * asserted here too to lock the behaviour in.</p>
 */
@ExtendWith(MockitoExtension.class)
class SummaryWrapRegressionTest {

    private static final long RESUME_ID = 1L;
    private static final long USER_ID = 7L;

    /** Three paragraphs separated by blank lines + an accented/Unicode line. */
    private static final String SUMMARY =
        "Results-driven platform engineer with 12+ years building accessible web products.\n"
            + "\n"
            + "Led a nine-person team that shipped a full redesign three weeks ahead of schedule.\n"
            + "\n"
            + "Résumé — QA ✓ Tamil: தமிழ்";

    private static final int EXPECTED_BREAKS = SUMMARY.split("\n", -1).length - 1;

    @Mock private ResumeRepository resumeRepository;
    @Mock private ExportQuotaService exportQuotaService;
    @Mock private ExportHistoryRepository exportHistoryRepository;
    @InjectMocks private ExportService exportService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).name("QA").email("qa@example.com").passwordHash("x").build();
        Resume resume = Resume.builder()
            .id(RESUME_ID)
            .userId(USER_ID)
            .title("Summary Wrap QA")
            .summary(SUMMARY)
            .build();
        when(resumeRepository.findByIdAndUserId(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));
    }

    @Test
    void docxPreservesSummaryParagraphBreaks() throws Exception {
        byte[] bytes = exportService.exportToDocx(user, RESUME_ID);
        String documentXml = unzipEntry(bytes, "word/document.xml");

        int breaks = countOccurrences(documentXml, "<w:br");
        assertEquals(EXPECTED_BREAKS, breaks,
            "DOCX summary must contain one hard line break per source newline, got " + breaks);

        // The text itself must survive (accents / Tamil included).
        assertTrue(documentXml.contains("Results-driven platform engineer"), "first paragraph missing");
        assertTrue(documentXml.contains("Led a nine-person team"), "second paragraph missing");
    }

    @Test
    void txtPreservesSummaryParagraphBreaks() {
        byte[] bytes = exportService.exportToTxt(user, RESUME_ID);
        String text = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(text.contains("products.\n\nLed a nine-person team"),
            "TXT must keep the blank line between summary paragraphs");
        assertTrue(text.contains("schedule.\n\nRésumé"), "TXT must keep the final paragraph break");
    }

    private static String unzipEntry(byte[] zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    in.transferTo(out);
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("Missing DOCX entry " + name);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = 0;
        while ((at = haystack.indexOf(needle, at)) >= 0) { count++; at += needle.length(); }
        return count;
    }
}
