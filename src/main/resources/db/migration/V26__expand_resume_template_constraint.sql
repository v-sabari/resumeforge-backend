-- ============================================================
-- V26__expand_resume_template_constraint.sql
-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
--
-- BUG FIX (TEMPLATE-01): the resumes.template CHECK constraint
-- (last rewritten in V19__remove_professional_template.sql) only
-- permitted SIX values:
--     modern, classic, minimal, executive, fresher, creative
-- but the product ships TWENTY templates. ResumeService.ALLOWED_TEMPLATES
-- and ResumeRequest's @Pattern both accept all 20, so saving any of the
-- other 14 templates (including FREE ones such as 'corporate', 'tech',
-- 'graduate', 'traditional', 'clean', 'engineering') passed application
-- validation and then failed at the DB CHECK constraint — making those
-- templates impossible to save, and therefore impossible to export.
--
-- This migration widens the constraint to exactly the 20 templates the
-- application understands. Keep this list in sync with
-- ResumeService.ALLOWED_TEMPLATES and frontend src/utils/constants.js
-- RESUME_TEMPLATES.
-- ============================================================

-- ── Step 1: Backfill any legacy value outside the new allowed set ────────────
UPDATE resumes
SET template = 'modern'
WHERE template IS NULL
   OR TRIM(template) = ''
   OR template NOT IN (
        'modern', 'corporate', 'classic', 'traditional', 'minimal', 'clean',
        'fresher', 'graduate', 'tech', 'engineering',
        'executive', 'leadership', 'creative', 'designer',
        'sleek', 'contemporary', 'academic', 'research', 'medical', 'finance'
   );

-- ── Step 2: Replace the narrow constraint with the full 20-template set ──────
ALTER TABLE resumes DROP CONSTRAINT IF EXISTS chk_resumes_template;

ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_template
    CHECK (template IN (
        'modern', 'corporate', 'classic', 'traditional', 'minimal', 'clean',
        'fresher', 'graduate', 'tech', 'engineering',
        'executive', 'leadership', 'creative', 'designer',
        'sleek', 'contemporary', 'academic', 'research', 'medical', 'finance'
    ));
