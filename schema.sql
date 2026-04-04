-- CVCraft AI — Production Database Schema
-- PostgreSQL 15+
-- Run this once on a fresh database. JPA ddl-auto=update handles incremental changes.

-- ── Users ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120)  NOT NULL,
    email         VARCHAR(160)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    is_premium    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (LOWER(email));

-- ── Resumes ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS resumes (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL,
    full_name            VARCHAR(180),
    role                 VARCHAR(180),
    email                VARCHAR(180),
    phone                VARCHAR(60),
    location             VARCHAR(180),
    linkedin             VARCHAR(255),
    github               VARCHAR(255),
    portfolio            VARCHAR(255),
    summary              TEXT,
    skills_json          TEXT,
    certifications_json  TEXT,
    achievements_json    TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_resumes_user_updated ON resumes (user_id, updated_at DESC);

-- ── Experiences ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS experiences (
    id           BIGSERIAL PRIMARY KEY,
    resume_id    BIGINT      NOT NULL,
    company      VARCHAR(180),
    role         VARCHAR(180),
    location     VARCHAR(180),
    start_date   VARCHAR(50),
    end_date     VARCHAR(50),
    bullets_json TEXT,
    CONSTRAINT fk_experiences_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_experiences_resume ON experiences (resume_id);

-- ── Education ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS education (
    id          BIGSERIAL PRIMARY KEY,
    resume_id   BIGINT      NOT NULL,
    institution VARCHAR(220),
    degree      VARCHAR(220),
    field       VARCHAR(180),
    start_date  VARCHAR(50),
    end_date    VARCHAR(50),
    CONSTRAINT fk_education_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_education_resume ON education (resume_id);

-- ── Projects ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS projects (
    id          BIGSERIAL PRIMARY KEY,
    resume_id   BIGINT      NOT NULL,
    name        VARCHAR(220),
    link        VARCHAR(255),
    description TEXT,
    CONSTRAINT fk_projects_resume FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_projects_resume ON projects (resume_id);

-- ── Payments ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    payment_id  VARCHAR(120)    NOT NULL UNIQUE,
    amount      DECIMAL(10,2)   NOT NULL,
    status      VARCHAR(40)     NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_payments_user ON payments (user_id, created_at DESC);

-- ── Ad Events ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ad_events (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    status          VARCHAR(30) NOT NULL,
    used_for_export BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ad_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ad_events_user_status ON ad_events (user_id, status, created_at DESC);

-- ── Export Usage ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS export_usage (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT    NOT NULL,
    export_count INT       NOT NULL,
    ad_completed BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_export_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_export_usage_user ON export_usage (user_id, created_at DESC);
