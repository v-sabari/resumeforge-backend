-- AI USAGE LOG TABLE
CREATE TABLE IF NOT EXISTS ai_usage_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    feature VARCHAR(100) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users(id)
);