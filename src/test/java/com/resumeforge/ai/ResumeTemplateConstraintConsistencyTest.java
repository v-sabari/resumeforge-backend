package com.resumeforge.ai;

import com.resumeforge.ai.service.ResumeService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for TEMPLATE-01.
 *
 * The product ships 20 templates and {@link ResumeService#ALLOWED_TEMPLATES}
 * accepts all of them, but the resumes.template CHECK constraint had been left
 * at only 6 values (V19), so 14 templates could be selected in the UI and then
 * fail to save at the database. V26 widens the constraint to all 20. This test
 * fails if the migration and the service's allowed set ever drift apart again.
 */
class ResumeTemplateConstraintConsistencyTest {

    private static final String MIGRATION = "/db/migration/V26__expand_resume_template_constraint.sql";

    private String migrationSql() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(MIGRATION)) {
            assertNotNull(in, "Migration " + MIGRATION + " must exist on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migrationIncludesEveryAllowedTemplate() throws Exception {
        String sql = migrationSql();
        for (String template : ResumeService.ALLOWED_TEMPLATES) {
            assertTrue(sql.contains("'" + template + "'"),
                    "V26 CHECK constraint is missing allowed template '" + template + "'");
        }
    }

    @Test
    void allowedTemplateCountIsTwenty() {
        assertEquals(20, ResumeService.ALLOWED_TEMPLATES.size(),
                "The product advertises exactly 20 templates");
        assertEquals(10, ResumeService.PREMIUM_TEMPLATES.size(),
                "Exactly 10 of the 20 templates are premium-exclusive");
    }

    @Test
    void premiumTemplatesAreASubsetOfAllowed() {
        assertTrue(ResumeService.ALLOWED_TEMPLATES.containsAll(ResumeService.PREMIUM_TEMPLATES),
                "Every premium template must also be an allowed template");
    }
}
