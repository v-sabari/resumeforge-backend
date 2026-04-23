-- =========================================
-- V16__referral_runtime_alignment.sql
-- PostgreSQL-safe alignment for current entities and referral flow
-- =========================================

-- USERS: align with current User.java
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(30) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_premium BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS premium_expires_at TIMESTAMP NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_otp VARCHAR(20);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_otp_expires_at TIMESTAMP;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referral_code VARCHAR(12);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS referred_by_user_id BIGINT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS has_created_resume BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_referral_code_v16
    ON users(referral_code);

CREATE INDEX IF NOT EXISTS idx_users_referred_by_user_id_v16
    ON users(referred_by_user_id);

-- Backfill from alternate legacy columns when present
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'email_verified'
    ) THEN
        UPDATE users
        SET email_verified = COALESCE(email_verified, FALSE);
    END IF;
END $$;

-- RESUMES: ensure scalar user_id model is supported
ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_resumes_user_id_v16
    ON resumes(user_id);

-- RESUME SNAPSHOTS
CREATE TABLE IF NOT EXISTS resume_snapshots (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resume_snapshots_resume_id_v16
    ON resume_snapshots(resume_id);

-- REFERRAL HISTORY
CREATE TABLE IF NOT EXISTS referral_history (
    id BIGSERIAL PRIMARY KEY,
    referrer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referred_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    resume_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    qualified_at TIMESTAMP NULL,
    CONSTRAINT uk_referral_history_referred_user_v16 UNIQUE (referred_user_id),
    CONSTRAINT chk_referral_history_status_v16 CHECK (status IN ('PENDING', 'QUALIFIED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_referral_history_referrer_v16
    ON referral_history(referrer_user_id);

CREATE INDEX IF NOT EXISTS idx_referral_history_referred_v16
    ON referral_history(referred_user_id);

CREATE INDEX IF NOT EXISTS idx_referral_history_status_v16
    ON referral_history(status);

-- REFERRAL REWARDS: align with current entity even if legacy columns still exist
ALTER TABLE referral_rewards
    ADD COLUMN IF NOT EXISTS description VARCHAR(255);

ALTER TABLE referral_rewards
    ADD COLUMN IF NOT EXISTS milestone_count INTEGER;

ALTER TABLE referral_rewards
    ADD COLUMN IF NOT EXISTS granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE referral_rewards
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_referral_rewards_user_milestone_v16
    ON referral_rewards(user_id, milestone_count);