-- ============================================================
-- V17__resume_template_constraint_hardening.sql
-- Rewritten for MySQL 8.0 (originally PostgreSQL).
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

-- ── Step 3 + 4: Enforce NOT NULL and set default in one statement ────────────
-- (MySQL combines "SET NOT NULL" and "SET DEFAULT" into a single MODIFY COLUMN.)
ALTER TABLE resumes
    MODIFY COLUMN template VARCHAR(100) NOT NULL DEFAULT 'modern';

-- ── Step 5: Add CHECK constraint ─────────────────────────────────────────────
-- This is the first time this constraint is created (fresh MySQL DB via
-- Flyway), so no existence guard is needed.
ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_template
    CHECK (template IN (
        'modern', 'classic', 'minimal', 'professional',
        'executive', 'fresher', 'creative'
    ));

-- ── Step 6: Ensure JSON columns exist with correct type ──────────────────────
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS personal_info   JSON;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS experience      JSON;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS education       JSON;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS skills          JSON;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS projects        JSON;

-- ── Step 7: Ensure title NOT NULL with safe default ───────────────────────────
UPDATE resumes
SET title = 'Untitled Resume'
WHERE title IS NULL OR TRIM(title) = '';

ALTER TABLE resumes
    MODIFY COLUMN title VARCHAR(500) NOT NULL;

-- ── Verification comment (for manual audit) ───────────────────────────────────
-- After this migration, running:
--
--   SELECT CONSTRAINT_NAME, CHECK_CLAUSE
--   FROM   information_schema.CHECK_CONSTRAINTS
--   WHERE  CONSTRAINT_NAME = 'chk_resumes_template';
--
-- And:
--   SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT
--   FROM   information_schema.COLUMNS
--   WHERE  TABLE_NAME = 'resumes' AND COLUMN_NAME = 'template';
--
-- Should return:
--   template | NO | modern
