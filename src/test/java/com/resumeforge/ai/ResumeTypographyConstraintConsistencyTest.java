package com.resumeforge.ai;

import com.resumeforge.ai.dto.ResumeRequest;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.service.ResumeService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the resume-only typography feature (V27, widened by V28).
 *
 * <p>The font family is an allow-listed identifier, not a CSS value, and the
 * size is bounded, so a client can never inject arbitrary fonts/CSS through
 * these fields. Four independent layers enforce that: the DTO annotations, the
 * service sanitizers, the entity lifecycle hooks and the database CHECK
 * constraints. This test keeps all four (plus the frontend list) in sync.</p>
 *
 * <p>V28 widens only the font-size upper bound (13.0 -> 16.0). It must not
 * rewrite any stored value, so a resume holding 10.5 or 13.0 is read back
 * unchanged.</p>
 */
class ResumeTypographyConstraintConsistencyTest {

    private static final String MIGRATION = "/db/migration/V27__add_resume_typography.sql";
    private static final String MIGRATION_V28 = "/db/migration/V28__widen_font_size_range.sql";

    private static Validator validator;
    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) validatorFactory.close();
    }

    private String migrationSql(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "Migration " + resource + " must exist on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migrationIncludesEveryAllowedFontFamily() throws Exception {
        String sql = migrationSql(MIGRATION);
        for (String family : ResumeService.ALLOWED_FONT_FAMILIES) {
            assertTrue(sql.contains("'" + family + "'"),
                    "V27 CHECK constraint is missing allowed font family '" + family + "'");
        }
    }

    @Test
    void allowedFontFamilyCountIsNine() {
        assertEquals(9, ResumeService.ALLOWED_FONT_FAMILIES.size(),
                "template + 8 traditional families");
        assertTrue(ResumeService.ALLOWED_FONT_FAMILIES.contains(ResumeService.DEFAULT_FONT_FAMILY));
    }

    @Test
    void dtoAcceptsEveryAllowedFamilyAndRejectsRawCss() {
        for (String family : ResumeService.ALLOWED_FONT_FAMILIES) {
            assertEquals(Set.of(), fontFamilyViolations(family),
                    "fontFamily '" + family + "' must be accepted");
        }

        // A raw CSS injection attempt must be rejected by @Pattern.
        assertTrue(fontFamilyViolations("Times New Roman; color: red").size() > 0,
                "A raw CSS value must not pass fontFamily validation");
        assertTrue(fontFamilyViolations("comic-sans").size() > 0,
                "An unlisted family must not pass fontFamily validation");
    }

    @Test
    void dtoBoundsFontSizeToEightThroughSixteen() {
        assertEquals(Set.of(), fontSizeViolations(8.0),
                "8.0 is the inclusive lower bound");
        assertEquals(Set.of(), fontSizeViolations(13.0),
                "13.0 (the old ceiling) must remain accepted after V28");
        assertEquals(Set.of(), fontSizeViolations(16.0),
                "16.0 is the inclusive upper bound after V28");
        assertTrue(!fontSizeViolations(7.9).isEmpty(),
                "7.9 must be rejected as too small");
        assertTrue(!fontSizeViolations(16.1).isEmpty(),
                "16.1 must be rejected as too large");
    }

    @Test
    void serviceSanitizersNormaliseAndFailClosed() throws Exception {
        ResumeService service = new ResumeService();

        Method family = ResumeService.class
                .getDeclaredMethod("sanitizeFontFamily", String.class);
        family.setAccessible(true);
        assertEquals("times", family.invoke(service, "  TIMES "),
                "Known families are trimmed and lower-cased");
        assertEquals("template", family.invoke(service, "comic-sans"),
                "Unknown family falls back to the template default");
        assertEquals("template", family.invoke(service, (Object) null),
                "Null family falls back to the template default");

        Method size = ResumeService.class
                .getDeclaredMethod("sanitizeFontSize", Double.class);
        size.setAccessible(true);
        assertEquals(10.5, size.invoke(service, (Object) null),
                "Null size uses the designed baseline");
        assertEquals(10.5, size.invoke(service, 7.5),
                "Below-range size fails closed to the baseline");
        assertEquals(10.5, size.invoke(service, 16.1),
                "Above-range size fails closed to the baseline");
        assertEquals(11.0, size.invoke(service, 11.04),
                "Sizes are rounded to one decimal");

        // V28 must not rewrite stored values: existing in-range sizes survive
        // the sanitizer byte-for-byte.
        assertEquals(10.5, size.invoke(service, 10.5),
                "Stored 10.5 is read back unchanged");
        assertEquals(13.0, size.invoke(service, 13.0),
                "Stored 13.0 is read back unchanged (not clamped to a new bound)");
    }

    @Test
    void entityLifecycleHooksRepairInvalidTypography() {
        Resume resume = new Resume();
        resume.setTemplate("modern");
        resume.setFontFamily(null);
        resume.setFontSize(99.0);

        resume.prePersist();

        assertEquals(ResumeService.DEFAULT_FONT_FAMILY, resume.getFontFamily());
        assertEquals(ResumeService.DEFAULT_FONT_SIZE, resume.getFontSize());

        resume.setFontFamily("times");
        resume.setFontSize(12.0);
        resume.preUpdate();
        assertEquals("times", resume.getFontFamily());
        assertEquals(12.0, resume.getFontSize());

        // Widened ceiling: 13.0 and 16.0 are both valid and must be preserved.
        resume.setFontSize(13.0);
        resume.preUpdate();
        assertEquals(13.0, resume.getFontSize(), "13.0 survives the entity hook");
        resume.setFontSize(16.0);
        resume.preUpdate();
        assertEquals(16.0, resume.getFontSize(), "16.0 is accepted after V28");
    }

    @Test
    void v28MigrationWidensRangeWithoutRewritingData() throws Exception {
        // Strip `--` line comments so the prose in the migration header cannot
        // masquerade as a statement.
        String sql = migrationSql(MIGRATION_V28)
                .lines()
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .toUpperCase();

        assertTrue(sql.contains("16.0"), "V28 must set the new 16.0 upper bound");
        assertTrue(sql.contains("8.0"), "V28 must keep the 8.0 lower bound");
        assertTrue(sql.contains("NOT VALID"),
                "V28 must add the CHECK as NOT VALID first (avoids a full-table lock)");
        assertTrue(sql.contains("VALIDATE CONSTRAINT"),
                "V28 must validate the constraint in a separate statement");

        // No data rewrite: the migration must never touch existing rows.
        assertFalse(sql.contains("UPDATE "), "V28 must not UPDATE any row");
        assertFalse(sql.contains("DELETE "), "V28 must not DELETE any row");
    }

    /* ── helpers ─────────────────────────────────────────────────── */

    private Set<String> fontFamilyViolations(String value) {
        ResumeRequest req = validRequest();
        req.setFontFamily(value);
        return paths(validator.validate(req));
    }

    private Set<String> fontSizeViolations(double value) {
        ResumeRequest req = validRequest();
        req.setFontSize(value);
        return paths(validator.validate(req));
    }

    private ResumeRequest validRequest() {
        ResumeRequest req = new ResumeRequest();
        req.setTitle("Test resume");
        req.setTemplate("modern");
        req.setFontFamily("template");
        req.setFontSize(10.5);
        return req;
    }

    private Set<String> paths(Set<ConstraintViolation<ResumeRequest>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
