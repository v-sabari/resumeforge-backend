-- V1__initial_schema.sql
-- ResumeForge AI - Initial Database Schema

-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_otp VARCHAR(6),
    email_otp_expires_at TIMESTAMP,
    password_reset_token VARCHAR(255),
    password_reset_expires_at TIMESTAMP,
    referral_code VARCHAR(20) UNIQUE,
    referred_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_users_referred_by FOREIGN KEY (referred_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_referral_code ON users(referral_code);
CREATE INDEX idx_users_referred_by ON users(referred_by_user_id);

-- Resumes table
CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    template VARCHAR(100) NOT NULL DEFAULT 'modern',
    personal_info JSONB,
    summary TEXT,
    experience JSONB,
    education JSONB,
    skills JSONB,
    projects JSONB,
    certifications JSONB,
    custom_sections JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_resumes_user_id ON resumes(user_id);
CREATE INDEX idx_resumes_created_at ON resumes(created_at);

-- Resume history / snapshots
CREATE TABLE resume_snapshots (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_snapshots_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);

CREATE INDEX idx_snapshots_resume_id ON resume_snapshots(resume_id);
CREATE INDEX idx_snapshots_created_at ON resume_snapshots(created_at);

-- Payments table
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    razorpay_order_id VARCHAR(255),
    razorpay_payment_id VARCHAR(255),
    razorpay_signature VARCHAR(255),
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(100),
    invoice_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_razorpay_order_id ON payments(razorpay_order_id);
CREATE INDEX idx_payments_razorpay_payment_id ON payments(razorpay_payment_id);
CREATE INDEX idx_payments_status ON payments(status);

-- Export history
CREATE TABLE export_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id BIGINT,
    export_format VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_export_history_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_export_history_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE SET NULL
);

CREATE INDEX idx_export_history_user_id ON export_history(user_id);
CREATE INDEX idx_export_history_created_at ON export_history(created_at);

-- Contact messages
CREATE TABLE contact_messages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_contact_messages_status ON contact_messages(status);
CREATE INDEX idx_contact_messages_created_at ON contact_messages(created_at);

-- AI usage tracking
CREATE TABLE ai_usage_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    feature VARCHAR(100) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_ai_usage_user_id ON ai_usage_log(user_id);
CREATE INDEX idx_ai_usage_feature ON ai_usage_log(feature);
CREATE INDEX idx_ai_usage_created_at ON ai_usage_log(created_at);

-- Ad flow tracking
CREATE TABLE ad_flow_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ad_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ad_flow_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_ad_flow_user_id ON ad_flow_log(user_id);
CREATE INDEX idx_ad_flow_created_at ON ad_flow_log(created_at);
