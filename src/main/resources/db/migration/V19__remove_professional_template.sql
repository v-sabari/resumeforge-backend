-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
UPDATE resumes SET template = 'modern' WHERE template = 'professional';

-- The constraint was created in V17 on this same DB, so it is guaranteed
-- to exist here. PostgreSQL uses DROP CONSTRAINT (not DROP CHECK, which
-- is MySQL-only syntax).
ALTER TABLE resumes DROP CONSTRAINT chk_resumes_template;

ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_template
    CHECK (template IN ('modern','classic','minimal','executive','fresher','creative'));
