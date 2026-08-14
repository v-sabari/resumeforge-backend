-- ============================================================
-- V17__resume_template_constraint_hardening.sql
-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
--
-- RES-01 FIX: Harden the resumes table to reject invalid template
-- values at the DB level (defence-in-depth beyond Spring validation).
--
-- ALLOWED SET (must stay in sync with ResumeService.ALLOWED_TEMPLATES):
--   modern, classic, minimal, professional, executive, fresher, creative
-- ============================================================

-- ── Step 1: Backfill NULL / blank template values ────────────────────────────
UPDATE resumes
SET template = 'modern'
WHERE template IS NULL
   OR TRIM(template) = '';

-- ── Step 2: Backfill invalid template values (not in allowed set) ────────────
UPDATE resumes
SET template = 'modern'
WHERE template NOT IN (
    'modern', 'classic', 'minimal', 'professional',
    'executive', 'fresher', 'creative'
);

-- ── Step 3 + 4: Enforce NOT NULL and set default ─────────────────────────────
-- PostgreSQL has no MODIFY COLUMN; type/default/not-null are three separate
-- ALTER COLUMN clauses.
ALTER TABLE resumes
    ALTER COLUMN template TYPE VARCHAR(100);

ALTER TABLE resumes
    ALTER COLUMN template SET DEFAULT 'modern';

ALTER TABLE resumes
    ALTER COLUMN template SET NOT NULL;

-- ── Step 5: Add CHECK constraint ─────────────────────────────────────────────
-- This is the first time this constraint is created (fresh Postgres DB via
-- Flyway), so no existence guard is needed.
ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_template
    CHECK (template IN (
        'modern', 'classic', 'minimal', 'professional',
        'executive', 'fresher', 'creative'
    ));

-- ── Step 6: (removed) ─────────────────────────────────────────────────────────
-- personal_info/experience/education/skills/projects JSON columns are already
-- created by V1__initial_schema.sql. Re-adding them here would fail with
-- "column already exists".

-- ── Step 7: Ensure title NOT NULL with safe default ───────────────────────────
UPDATE resumes
SET title = 'Untitled Resume'
WHERE title IS NULL OR TRIM(title) = '';

ALTER TABLE resumes
    ALTER COLUMN title TYPE VARCHAR(500);

ALTER TABLE resumes
    ALTER COLUMN title SET NOT NULL;

-- ── Verification comment (for manual audit) ───────────────────────────────────
-- After this migration, running:
--
--   SELECT conname, pg_get_constraintdef(oid)
--   FROM   pg_constraint
--   WHERE  conname = 'chk_resumes_template';
--
-- And:
--   SELECT column_name, is_nullable, column_default
--   FROM   information_schema.columns
--   WHERE  table_name = 'resumes' AND column_name = 'template';
--
-- Should return:
--   template | NO | 'modern'::character varying
