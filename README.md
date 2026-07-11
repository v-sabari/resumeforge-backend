# ResumeForge AI — Backend

Production-grade Spring Boot backend for the **ResumeForge AI** SaaS platform — AI-assisted resume building, ATS scoring, exports, payments, and referrals.

## Tech Stack

| Layer          | Tech                              |
|-----------------|-------------------------------------|
| Language        | Java 21                             |
| Framework       | Spring Boot 3.2.5                   |
| Database        | PostgreSQL (Neon)                   |
| Migrations      | Flyway                              |
| Auth            | JWT (jjwt) + Spring Security        |
| AI              | OpenRouter API                      |
| Payments        | Razorpay                            |
| Email           | Resend                              |
| PDF export      | Apache PDFBox                       |
| DOCX export     | Apache POI                          |
| Load testing    | k6 (see [`load-tests/`](./load-tests)) |
| Deployment      | Docker → Render                     |

## Features

- **Auth & authorization** — registration with email OTP, JWT login, password reset, role-based access (USER/ADMIN)
- **Resume management** — CRUD, version history/snapshots, multiple templates, flexible JSON schema
- **AI features** (via OpenRouter) — content rewriting, bullet improvement, summary generation, skill extraction, job tailoring, ATS scoring, cover letter generation, LinkedIn optimization, grammar check, interview prep
- **Export** — PDF, DOCX, ATS-safe TXT, export history, free-tier daily limits
- **Payments** — Razorpay order creation + signature-verified payment confirmation, premium subscription management, invoice emails
- **Referrals** — code generation, reward tracking, anti-abuse checks
- **Admin panel** — user management, payment analytics, AI usage stats, referral analytics

## Project Structure

```
src/main/java/com/resumeforge/ai/
├── config/          # Configuration (async, etc.)
├── controller/      # REST controllers
├── dto/             # Data Transfer Objects
├── entity/          # JPA entities
├── exception/       # Custom exceptions + global handler
├── repository/      # JPA repositories
├── security/        # JWT filter/util, security config, rate limiting
├── service/         # Business logic
└── util/            # Helpers (OTP, referral codes, tokens)

src/main/resources/
├── db/migration/    # Flyway SQL migrations (V1 … V22)
├── fonts/           # Fonts bundled for PDF export (PDFBox)
└── application.properties

load-tests/          # k6 load/health-check/stress scripts (see its README)
Dockerfile
verify-jar-fonts.sh  # Post-build check that fonts are bundled in the JAR
```

## Getting Started

### Prerequisites

- Java 21
- Maven 3.8+
- PostgreSQL 14+ (or a hosted instance, e.g. Neon)

### Setup

```bash
git clone <repo-url>
cd resumeforge-ai-backend
cp .env.example .env
```

Fill in `.env` with real values — see the table below. **The application will fail to start** if the database variables aren't set; there is intentionally no hardcoded fallback.

| Variable                       | Required | Description                                    |
|----------------------------------|:---:|-------------------------------------------------|
| `SPRING_DATASOURCE_URL`          | ✅  | JDBC URL of your PostgreSQL database             |
| `SPRING_DATASOURCE_USERNAME`     | ✅  | DB username                                      |
| `SPRING_DATASOURCE_PASSWORD`     | ✅  | DB password                                      |
| `APP_JWT_SECRET`                 | ✅  | 256-bit+ random secret for signing JWTs          |
| `OPENROUTER_API_KEY`             | ✅  | Key for AI features                              |
| `APP_CORS_ALLOWED_ORIGIN`        | ✅  | Comma-separated allowed origins                  |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | ✅ | Payment gateway credentials         |
| `RAZORPAY_WEBHOOK_SECRET`        | ✅  | For verifying Razorpay webhook signatures        |
| `RESEND_API_KEY`                 | ✅  | Transactional email                              |
| `RENDER_SERVICE_URL` / `RENDER_INTERNAL_SECRET` | ✅ | PDF render service (frontend's Vercel function) |

See `.env.example` for the full list with defaults/placeholders.

### Run locally

```bash
mvn spring-boot:run
```

Runs at `http://localhost:8080`. Flyway migrations run automatically on startup.

### Build

```bash
mvn clean package -DskipTests
```

Verify fonts are bundled correctly (required for PDF export):

```bash
bash verify-jar-fonts.sh
```

### Run with Docker

```bash
docker build -t resumeforge-ai-backend .
docker run -p 8080:8080 --env-file .env resumeforge-ai-backend
```

## API Overview

### Public
`POST /api/auth/register` · `POST /api/auth/login` · `POST /api/auth/verify-email-otp` · `POST /api/auth/forgot-password` · `POST /api/auth/reset-password` · `POST /api/contact`

### Protected (JWT required)
`GET /api/auth/me` · `GET|POST|PUT|DELETE /api/resumes` · `GET /api/resumes/{id}/history` · `POST /api/ai/*` · `GET /api/export/status` · `GET /api/export/download/{id}` · `POST /api/payments/create` · `POST /api/payments/verify` · `GET /api/premium/status` · `GET /api/referral/status`

### Admin (ADMIN role required)
`GET /api/admin/stats` · `GET /api/admin/users` · `POST /api/admin/users/{id}/role` · `POST /api/admin/users/{id}/toggle-premium` · `GET /api/admin/payments` · `GET /api/admin/ai-stats`

## Database Migrations

Managed by Flyway, in `src/main/resources/db/migration/`. To add a migration, create a new `V{n}__description.sql` file — never edit an already-applied migration.

## Load & Health Testing

See [`load-tests/README.md`](./load-tests/README.md) for k6 scripts covering normal load, stress testing, DB connection pooling, and graceful shutdown behavior.

## Security

- No secrets or credentials are hardcoded — all required config comes from environment variables with no insecure fallbacks (see `.env.example`)
- Passwords hashed with BCrypt
- JWT-based auth, OTP rate limiting, Razorpay signature verification, JPA-based SQL injection prevention
- See [SECURITY.md](./SECURITY.md) to report a vulnerability

## Production Deployment Notes

1. Set `spring.jpa.hibernate.ddl-auto=validate` or keep `none` with Flyway as the source of truth (current default)
2. Use a strong, unique JWT secret (256-bit minimum)
3. Enable HTTPS at the load balancer/host level
4. Restrict `APP_CORS_ALLOWED_ORIGIN` to your real frontend domain(s) only
5. Set up automated database backups
6. Monitor logs/metrics; the Dockerfile is tuned for container memory limits (see comments in `Dockerfile`)

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

This project is proprietary software. See [LICENSE](./LICENSE).
