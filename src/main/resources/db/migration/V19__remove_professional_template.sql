-- Rewritten for MySQL 8.0 (originally PostgreSQL).
UPDATE resumes SET template = 'modern' WHERE template = 'professional';

-- The constraint was created in V17 on this same DB, so it is guaranteed
-- to exist here — no existence guard needed (MySQL's DROP CHECK doesn't
-- support IF EXISTS the way Postgres's DROP CONSTRAINT does).
ALTER TABLE resumes DROP CHECK chk_resumes_template;

ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_template
    CHECK (template IN ('modern','classic','minimal','executive','fresher','creative'));
