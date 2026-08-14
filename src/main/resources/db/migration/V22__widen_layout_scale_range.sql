-- V22: Widens layout_scale to support the "grow to fill" direction of the
-- Compress feature, not just "shrink to fit". PostgreSQL syntax (target
-- database is Neon PostgreSQL 17.x).
--
-- V21 originally constrained layout_scale to (0.5, 1.0], i.e. shrink-only.
-- The feature now also supports enlarging sparse content to fully occupy
-- extra pages (e.g. 1.5 written pages stretched to fill 2), so the range
-- must allow values above 1.0 too — capped at 1.35 to match the frontend's
-- own professional-size ceiling (utils/compression.js MAX_SCALE).
--
-- The constraint was created in V21 on this same DB, so it is guaranteed
-- to exist here. PostgreSQL uses DROP CONSTRAINT (not DROP CHECK, which
-- is MySQL-only syntax).
ALTER TABLE resumes
    DROP CONSTRAINT chk_layout_scale_range;

ALTER TABLE resumes
    ADD CONSTRAINT chk_layout_scale_range CHECK (layout_scale >= 0.5 AND layout_scale <= 1.35);

-- Column type/not-null/default are unchanged from V21; only the comment
-- needs updating to reflect the new range. PostgreSQL sets this with a
-- separate COMMENT ON COLUMN statement, not an inline MODIFY COLUMN clause.
COMMENT ON COLUMN resumes.layout_scale IS
    'Compress feature density scale [0.5, 1.35]. 1.0 = full size/unchanged; <1.0 = shrunk to fit fewer pages; >1.0 = enlarged to fill more pages. Applied identically in preview and PDF export.';
