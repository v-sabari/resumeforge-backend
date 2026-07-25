-- Rewritten for MySQL 8.0 (originally PostgreSQL) — syntax is identical here.
ALTER TABLE resumes
ADD COLUMN IF NOT EXISTS achievements TEXT;
