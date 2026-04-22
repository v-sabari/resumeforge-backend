-- =========================
-- ad_flow_log sync
-- =========================
CREATE TABLE IF NOT EXISTS ad_flow_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ad_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ad_flow_user FOREIGN KEY (user_id) REFERENCES users(id)
);

ALTER TABLE ad_flow_log
ADD COLUMN IF NOT EXISTS user_id BIGINT,
ADD COLUMN IF NOT EXISTS ad_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS status VARCHAR(50),
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

UPDATE ad_flow_log
SET
    ad_type = COALESCE(ad_type, 'UNKNOWN'),
    status = COALESCE(status, 'UNKNOWN'),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE ad_type IS NULL OR status IS NULL OR created_at IS NULL;

-- =========================
-- ai_usage_log sync
-- =========================
CREATE TABLE IF NOT EXISTS ai_usage_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    feature VARCHAR(100) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- =========================
-- contact_messages sync
-- =========================
CREATE TABLE IF NOT EXISTS contact_messages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE contact_messages
ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'NEW',
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- =========================
-- export_history sync
-- =========================
CREATE TABLE IF NOT EXISTS export_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id BIGINT,
    export_format VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_export_history_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_export_history_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE SET NULL
);

-- =========================
-- payments sync
-- =========================
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'INR',
ADD COLUMN IF NOT EXISTS invoice_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- =========================
-- referral_rewards sync
-- =========================
CREATE TABLE IF NOT EXISTS referral_rewards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    referred_user_id BIGINT,
    reward_type VARCHAR(100) NOT NULL,
    reward_value NUMERIC(12,2),
    reward_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_referral_rewards_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_referral_rewards_referred_user FOREIGN KEY (referred_user_id) REFERENCES users(id)
);