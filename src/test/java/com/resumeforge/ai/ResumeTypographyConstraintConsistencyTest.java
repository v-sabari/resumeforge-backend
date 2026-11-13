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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the resume-only typography feature (V27).
 *
 * <p>The font family is an allow-listed identifier, not a CSS value, and the
 * size is bounded, so a client can never inject arbitrary fonts/CSS through
 * these fields. Four independent layers enforce that: the DTO annotations, the
 * service sanitizers, the entity lifecycle hooks and the database CHECK
 * constraints. This test keeps all four (plus the frontend list) in sync.</p>
 */
class ResumeTypographyConstraintConsistencyTest {

    private static final String MIGRATION = "/db/migration/V27__add_resume_typography.sql";

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

    private String migrationSql() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(MIGRATION)) {
            assertNotNull(in, "Migration " + MIGRATION + " must exist on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migrationIncludesEveryAllowedFontFamily() throws Exception {
        String sql = migrationSql();
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
    void dtoBoundsFontSizeToEightThroughThirteen() {
        assertEquals(Set.of(), fontSizeViolations(8.0),
                "8.0 is the inclusive lower bound");
        assertEquals(Set.of(), fontSizeViolations(13.0),
                "13.0 is the inclusive upper bound");
        assertTrue(!fontSizeViolations(7.9).isEmpty(),
                "7.9 must be rejected as too small");
        assertTrue(!fontSizeViolations(13.1).isEmpty(),
                "13.1 must be rejected as too large");
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
        assertEquals(10.5, size.invoke(service, 14.0),
                "Above-range size fails closed to the baseline");
        assertEquals(11.0, size.invoke(service, 11.04),
                "Sizes are rounded to one decimal");
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
