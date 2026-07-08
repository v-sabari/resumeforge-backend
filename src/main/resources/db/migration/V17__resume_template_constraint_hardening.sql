-- ============================================================
-- V17__resume_template_constraint_hardening.sql
--
-- RES-01 FIX: Harden the resumes table to reject invalid template
-- values at the DB level (defence-in-depth beyond Spring validation).
--
-- WHAT THIS MIGRATION DOES:
--
-- 1. Backfills any existing rows that have a NULL or blank template
--    value to the safe default "modern". This must happen BEFORE the
--    NOT NULL enforcement and CHECK constraint are added, otherwise
--    those statements would fail on existing dirty data.
--
-- 2. Backfills any existing rows where template is not in the allowed
--    set (e.g., "modern-pro", "executive-plus", empty string) to
--    "modern".  This covers rows written before this fix was deployed.
--
-- 3. Ensures the template column is NOT NULL (it was defined that way
--    in V1 and V14, but may have drifted on DBs that went through
--    ad-hoc alterations — ADD COLUMN IF NOT EXISTS without a NOT NULL
--    clause leaves nullability undefined on some Postgres versions).
--
-- 4. Sets the column DEFAULT to "modern" so INSERT statements that
--    omit template entirely still get a valid value without relying
--    solely on application-layer defaulting.
--
-- 5. Adds a CHECK constraint locking template to exactly the set of
--    values that ALLOWED_TEMPLATES in ResumeService.java recognises.
--    If a value somehow bypasses both DTO validation and service-layer
--    sanitization, the DB is the last line of defence.
--
-- ALLOWED SET (must stay in sync with ResumeService.ALLOWED_TEMPLATES):
--   modern, classic, minimal, professional, executive, fresher, creative
--
-- SAFE TO RUN ON EXISTING DATA: all statements are idempotent or use
-- IF NOT EXISTS / DO $$ guards.
-- ============================================================

-- ── Step 1: Backfill NULL / blank template values ────────────────────────────
UPDATE resumes
SET template = 'modern'
WHERE template IS NULL
   OR TRIM(template) = '';

-- ── Step 2: Backfill invalid template values (not in allowed set) ────────────
-- Covers rows written by old code that accepted arbitrary strings.
UPDATE resumes
SET template = 'modern'
WHERE template NOT IN (
    'modern', 'classic', 'minimal', 'professional',
    'executive', 'fresher', 'creative'
);

-- ── Step 3: Ensure NOT NULL is enforced on the column ────────────────────────
-- ALTER COLUMN ... SET NOT NULL fails if any NULL rows exist,
-- which is why the backfill in Step 1 must run first.
ALTER TABLE resumes
    ALTER COLUMN template SET NOT NULL;

-- ── Step 4: Set default so bare INSERTs omitting template are safe ────────────
ALTER TABLE resumes
    ALTER COLUMN template SET DEFAULT 'modern';

-- ── Step 5: Add CHECK constraint ─────────────────────────────────────────────
-- DO block guards against re-running on a DB that already has the constraint
-- (e.g., after a Flyway repair). Dropping and re-adding would be destructive;
-- we skip silently instead.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM   information_schema.table_constraints
        WHERE  table_schema    = 'public'
          AND  table_name      = 'resumes'
          AND  constraint_name = 'chk_resumes_template'
          AND  constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE resumes
            ADD CONSTRAINT chk_resumes_template
            CHECK (template IN (
                'modern', 'classic', 'minimal', 'professional',
                'executive', 'fresher', 'creative'
            ));
    END IF;
END $$;

-- ── Step 6: Ensure JSONB columns exist with correct type ─────────────────────
-- V14 added these with ADD COLUMN IF NOT EXISTS but some legacy DBs may have
-- them as TEXT. We cannot ALTER type in-place without losing data, so we only
-- add missing columns — if they already exist as JSONB (from V1/V14), this is a
-- no-op. If a column exists as TEXT, leave it — Hibernate's @JdbcTypeCode(JSON)
-- handles TEXT-backed JSON transparently on reads; it only fails on blind INSERT.
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS personal_info   JSONB;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS experience      JSONB;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS education       JSONB;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS skills          JSONB;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS projects        JSONB;



-- ── Step 7: Ensure title NOT NULL with safe default ───────────────────────────
-- V14 already does this, but only if the column existed before.
-- On fresh DBs built from V1 it is NOT NULL by definition.
-- On legacy DBs where title was added later via V14 ADD COLUMN IF NOT EXISTS
-- without NOT NULL, this makes it explicit.
UPDATE resumes
SET title = 'Untitled Resume'
WHERE title IS NULL OR TRIM(title) = '';

ALTER TABLE resumes
    ALTER COLUMN title SET NOT NULL;

-- ── Verification comment (for manual audit) ───────────────────────────────────
-- After this migration, running:
--
--   SELECT constraint_name, check_clause
--   FROM   information_schema.check_constraints
--   WHERE  constraint_name = 'chk_resumes_template';
--
-- Should return:
--   chk_resumes_template | (template = ANY (ARRAY[...]))
--
-- And:
--   SELECT column_name, is_nullable, column_default
--   FROM   information_schema.columns
--   WHERE  table_name = 'resumes' AND column_name = 'template';
--
-- Should return:
--   template | NO | modern
