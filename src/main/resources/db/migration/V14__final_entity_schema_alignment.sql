-- =========================================
-- V14__final_entity_schema_alignment.sql
-- Final sync for entity schema vs actual DB
-- =========================================

-- -------------------------
-- PAYMENTS
-- Entity needs:
-- razorpay_signature, payment_method, updated_at
-- -------------------------
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS razorpay_signature VARCHAR(255);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(100);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;


-- -------------------------
-- USERS
-- Entity maps to exact names:
-- password, is_email_verified, email_otp, email_otp_expires_at,
-- password_reset_token, password_reset_expires_at,
-- referral_code, referred_by_user_id, updated_at
-- Existing DB has legacy columns like password_hash, email_verified
-- -------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password VARCHAR(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_otp VARCHAR(6);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_otp_expires_at TIMESTAMP;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referral_code VARCHAR(20);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referred_by_user_id BIGINT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Safe backfill from legacy columns if they exist
UPDATE users
SET password = password_hash
WHERE password IS NULL
  AND EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'users'
        AND column_name = 'password_hash'
  );

UPDATE users
SET is_email_verified = email_verified
WHERE EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'users'
        AND column_name = 'email_verified'
  );

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_referral_code_v14
ON users(referral_code);


-- -------------------------
-- RESUMES
-- Entity needs modern JSONB-based schema
-- Existing DB is legacy profile-column style
-- -------------------------
ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS title VARCHAR(500);

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS template VARCHAR(100) NOT NULL DEFAULT 'modern';

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS personal_info JSONB;

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS experience JSONB;

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS education JSONB;

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS skills JSONB;

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS projects JSONB;

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS certifications JSONB;

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS custom_sections JSONB;

-- Entity requires title NOT NULL, so ensure data exists first
UPDATE resumes
SET title = COALESCE(title, 'Untitled Resume')
WHERE title IS NULL;

ALTER TABLE resumes
    ALTER COLUMN title SET NOT NULL;


-- -------------------------
-- RESUME SNAPSHOTS
-- Table likely missing
-- -------------------------
CREATE TABLE IF NOT EXISTS resume_snapshots (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resume_snapshots_resume_id_v14
ON resume_snapshots(resume_id);

CREATE INDEX IF NOT EXISTS idx_resume_snapshots_created_at_v14
ON resume_snapshots(created_at);


-- -------------------------
-- Optional helpful indexes for validate/runtime alignment
-- -------------------------
CREATE INDEX IF NOT EXISTS idx_users_referred_by_user_id_v14
ON users(referred_by_user_id);

CREATE INDEX IF NOT EXISTS idx_resumes_user_id_v14
ON resumes(user_id);

CREATE INDEX IF NOT EXISTS idx_payments_user_id_v14
ON payments(user_id);