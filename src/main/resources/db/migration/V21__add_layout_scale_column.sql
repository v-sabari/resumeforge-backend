-- V21: Adds layout_scale column to support the "Compress to N pages"
-- feature.
--
-- layout_scale is a density factor in the range (0.5, 1.0]:
--   1.0   = full size / uncompressed (default for all existing resumes)
--   < 1.0 = the resume has been compressed; this is the exact scale that
--           was verified, via real re-measurement in the browser (see
--           frontend utils/compression.js), to fit the user's chosen page
--           count for the template they had selected at the time.
--
-- This value is round-tripped unchanged into PDF export (ExportService
-- .buildRenderPayload -> renderPdfHandler.jsx) so the exported file always
-- matches what the user confirmed in the live preview — never a
-- re-derived or re-guessed value.
ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS layout_scale DOUBLE PRECISION NOT NULL DEFAULT 1.0;

ALTER TABLE resumes
    ADD CONSTRAINT chk_layout_scale_range CHECK (layout_scale >= 0.5 AND layout_scale <= 1.0);

COMMENT ON COLUMN resumes.layout_scale IS
    'Compress feature density scale (0.5, 1.0]. 1.0 = full size. Applied identically in preview and PDF export.';
