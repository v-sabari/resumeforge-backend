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

-- Step 1: Backfill NULL / blank template values
UPDATE resumes
SET template = 'modern'
WHERE template IS NULL
   OR TRIM(template) = '';

-- Step 2: Backfill invalid template values (not in allowed set)
UPDATE resumes
SET template = 'modern'
WHERE template NOT IN (
    'modern', 'classic', 'minimal', 'professional',
    'executive', 'fresher', 'creative'
);

-- Step 3 + 4: Enforce NOT NULL and set default in one statement
ALTER TABLE resumes
    MODIFY COLUMN template VARCHAR(100) NOT NULL DEFAULT 'modern';

-- Step 5: Add CHECK constraint
ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_template
    CHECK (template IN (
        'modern', 'classic', 'minimal', 'professional',
        'executive', 'fresher', 'creative'
    ));

-- Step 6: Ensure JSON columns exist with correct type
ALTER TABLE resumes ADD COLUMN personal_info   JSON;
ALTER TABLE resumes ADD COLUMN experience      JSON;
ALTER TABLE resumes ADD COLUMN education       JSON;
ALTER TABLE resumes ADD COLUMN skills          JSON;
ALTER TABLE resumes ADD COLUMN projects        JSON;

-- Step 7: Ensure title NOT NULL with safe default
UPDATE resumes
SET title = 'Untitled Resume'
WHERE title IS NULL OR TRIM(title) = '';

ALTER TABLE resumes
    MODIFY COLUMN title VARCHAR(500) NOT NULL;
