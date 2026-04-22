ALTER TABLE users
    ADD COLUMN IF NOT EXISTS premium_expires_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS referral_code VARCHAR(12) NULL,
    ADD COLUMN IF NOT EXISTS has_created_resume BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_referral_code ON users(referral_code);

ALTER TABLE referral_rewards
    ADD COLUMN IF NOT EXISTS description VARCHAR(255),
    ADD COLUMN IF NOT EXISTS milestone_count INTEGER,
    ADD COLUMN IF NOT EXISTS granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_referral_rewards_user_milestone
    ON referral_rewards(user_id, milestone_count);

CREATE TABLE IF NOT EXISTS referral_history (
    id BIGSERIAL PRIMARY KEY,
    referrer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referred_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    resume_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    qualified_at TIMESTAMP NULL,
    CONSTRAINT uk_referral_history_referred_user UNIQUE (referred_user_id),
    CONSTRAINT chk_referral_history_status CHECK (status IN ('PENDING', 'QUALIFIED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_referral_history_referrer ON referral_history(referrer_user_id);
CREATE INDEX IF NOT EXISTS idx_referral_history_referred ON referral_history(referred_user_id);
CREATE INDEX IF NOT EXISTS idx_referral_history_status ON referral_history(status);
