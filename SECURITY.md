# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in the ResumeForge AI backend,
please **do not** open a public GitHub issue.

Instead, report it privately:

- Email: **security@resumeforgeai.site** (replace with your real contact)
- Or use GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability) if enabled on this repo

Please include a description, reproduction steps or proof of concept, and
any relevant logs.

We aim to acknowledge reports within 3 business days.

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x     | ✅        |

## Handling Secrets

- All credentials (database, JWT secret, API keys, payment keys) are
  supplied exclusively via environment variables. `application.properties`
  intentionally has **no hardcoded fallback values** for any secret —
  the application will fail to start if required variables are missing.
- If you ever find a real credential, connection string, or API key
  committed anywhere in this repo or its git history, treat it as
  compromised: rotate it immediately at the provider, then report it
  through the channel above.
