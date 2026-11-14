-- V28: Widen the resume font_size CHECK upper bound 13.0 -> 16.0.
-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
--
-- WHY: the frontend typography control now presents the body size in points
-- (pt) rather than px, and the product wants headroom above the old 13px
-- ceiling so the slider can later reach 12pt (= 16px). This migration changes
-- ONLY the upper bound of the existing CHECK constraint; the lower bound
-- (8.0) is unchanged.
--
-- NO DATA REWRITE: this migration never UPDATEs a row. Every value already
-- stored (including 10.5 and 13.0) is read back byte-for-byte identical. The
-- widening is purely additive — 13.0 was, and remains, valid; the only new
-- thing is that 13.0 < font_size <= 16.0 is now accepted too.
--
-- LOCKING: a plain `ADD CONSTRAINT ... CHECK` validates every existing row
-- while holding a strong lock, which blocks writes on a large table. The
-- two-step NOT VALID -> VALIDATE pattern avoids that:
--   1. ADD CONSTRAINT ... NOT VALID  — records the rule for NEW/updated rows
--      immediately, without scanning the table (brief lock only).
--   2. VALIDATE CONSTRAINT           — scans existing rows under a weaker lock
--      (SHARE UPDATE EXCLUSIVE) that does not block reads or writes.
-- On this table both steps are trivial, but the pattern keeps the migration
-- safe to re-run against a large production table.
--
-- ROLLBACK: this widening is one-way. Once any row holds a value above 13.0,
-- re-applying the old 13.0 CHECK would fail validation. The supported rollback
-- is therefore to disable the larger range in the FRONTEND (lower
-- MAX_FONT_SIZE back to 9.75pt = 13px) and leave this constraint widened —
-- never to shrink the CHECK while out-of-range rows exist.
--
-- Idempotent constraint replacement (safe on re-run, like V27).
ALTER TABLE resumes DROP CONSTRAINT IF EXISTS chk_resumes_font_size;

ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_font_size
    CHECK (font_size >= 8.0 AND font_size <= 16.0) NOT VALID;

ALTER TABLE resumes
    VALIDATE CONSTRAINT chk_resumes_font_size;

COMMENT ON COLUMN resumes.font_size IS
    'Resume-only typography: body text size in px (8.0–16.0). 10.5 = the templates'' designed size.';
