-- MYSQL NOTE: "ADD COLUMN IF NOT EXISTS" is not valid MySQL syntax (that's a
-- Postgres/MariaDB feature). Removed since this is a fresh column add.
ALTER TABLE resumes
ADD COLUMN achievements TEXT;
