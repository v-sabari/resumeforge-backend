-- V27: Traditional resume typography (font family + font size).
-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
--
-- These two fields affect ONLY the resume rendering surface (live preview,
-- PDF export) — never the application's own UI. The frontend renders the
-- selected template inside a scoped .resume-typography wrapper and applies
-- the family/size there, so no global CSS is touched.
--
-- font_family is an allow-listed identifier (not a raw CSS value) so a
-- client can never inject arbitrary CSS through this column. NULL/unknown
-- values fall back to 'template', which means "use the font stack the
-- chosen template already ships with".
--
-- font_size is the body-text size in px. Templates are designed around a
-- 10.5px body; the allowed range (8.0–13.0) keeps output readable and
-- professional. The frontend converts it to a scoped zoom factor relative
-- to 10.5px so every absolute text-[Npx] class in the templates scales
-- uniformly. Defaults are the template's own size, so existing resumes
-- render exactly as before.
ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS font_family VARCHAR(32) NOT NULL DEFAULT 'template';

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS font_size DOUBLE PRECISION NOT NULL DEFAULT 10.5;

-- Idempotent constraint replacement (safe on re-run, like V26).
ALTER TABLE resumes DROP CONSTRAINT IF EXISTS chk_resumes_font_family;
ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_font_family
    CHECK (font_family IN (
        'template', 'times', 'georgia', 'garamond', 'arial',
        'helvetica', 'calibri', 'cambria', 'courier'
    ));

ALTER TABLE resumes DROP CONSTRAINT IF EXISTS chk_resumes_font_size;
ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_font_size
    CHECK (font_size >= 8.0 AND font_size <= 13.0);

COMMENT ON COLUMN resumes.font_family IS
    'Resume-only typography: allow-listed font family identifier. ''template'' = inherit the selected template''s own stack.';

COMMENT ON COLUMN resumes.font_size IS
    'Resume-only typography: body text size in px (8.0–13.0). 10.5 = the templates'' designed size.';
