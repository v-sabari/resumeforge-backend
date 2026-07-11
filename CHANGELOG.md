# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- Removed hardcoded database connection fallback (host/username/password) from
  `application.properties`. All datasource credentials now must be supplied via
  environment variables with no default — the app fails fast if missing.
- Removed hardcoded personal email fallback for `CONTACT_RECEIVER_EMAIL`.

### Added
- Standard repository documentation (README, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT, issue/PR templates, CI workflow)
- `load-tests/` folder with k6 load, stress, DB-connection, and graceful-shutdown scripts + README

## [1.0.0]

### Added
- Spring Boot 3.2.5 / Java 21 backend for ResumeForge AI
- JWT auth, OTP email verification, role-based access control
- Resume CRUD with version history
- OpenRouter-powered AI features (rewriting, ATS scoring, cover letters, etc.)
- PDF/DOCX/TXT export via PDFBox/POI
- Razorpay payment integration with webhook signature verification
- Referral system and admin analytics endpoints
- Flyway-managed PostgreSQL schema (V1–V22)
- Dockerized deployment tuned for Render's container memory limits
