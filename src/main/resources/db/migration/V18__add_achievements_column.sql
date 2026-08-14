-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
-- ADD COLUMN IF NOT EXISTS is valid in PostgreSQL (unlike MySQL), so it's
-- restored here as a safety guard for reruns.
ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS achievements TEXT;
