CREATE TABLE IF NOT EXISTS export_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id BIGINT,
    export_format VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_export_history_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT fk_export_history_resume
        FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE SET NULL
);