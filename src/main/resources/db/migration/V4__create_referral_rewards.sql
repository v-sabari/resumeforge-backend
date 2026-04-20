CREATE TABLE referral_rewards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    referred_user_id BIGINT,
    reward_type VARCHAR(100) NOT NULL,
    reward_value NUMERIC(12,2),
    reward_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_referral_rewards_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_referral_rewards_referred_user
        FOREIGN KEY (referred_user_id) REFERENCES users(id)
);