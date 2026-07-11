# Contributing to ResumeForge AI Backend

Thanks for taking the time to contribute. This guide covers how to get set up and how changes get merged.

## Getting Set Up

1. Fork and clone the repo
2. `cp .env.example .env` and fill in your local values (a local PostgreSQL instance or a Neon dev branch both work)
3. `mvn spring-boot:run`

See the [README](./README.md) for full setup details.

## Branching

- Branch off `main` (or `master`, whichever is this repo's default — check before branching)
- Use a descriptive prefix: `feat/…`, `fix/…`, `chore/…`, `docs/…`
- Keep branches focused on a single change

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add ATS score caching to AiService
fix: correct Razorpay webhook signature verification
docs: document RENDER_SERVICE_URL env variable
```

## Before Opening a PR

- [ ] `mvn clean package -DskipTests` builds successfully
- [ ] `bash verify-jar-fonts.sh` passes (if you touched PDF export or resources)
- [ ] New environment variables are documented in `.env.example` and the README
- [ ] No secrets, credentials, or `.env` files are included in the diff
- [ ] New database changes are added as a new Flyway migration (`V{n}__description.sql`), never editing an existing one

## Pull Requests

- Fill out the PR template
- Link any related issue
- Keep PRs small and reviewable where possible

## Database Migrations

- Migrations live in `src/main/resources/db/migration/`
- Never modify an already-committed migration file — Flyway checksums will break for anyone who already applied it
- Name new files `V{next_number}__short_description.sql`

## Security

- Never hardcode credentials, API keys, or connection strings — everything must come through environment variables (see `.env.example`)
- Do not open a public issue for security vulnerabilities — see [SECURITY.md](./SECURITY.md)

## Load Testing

If your change affects performance-sensitive paths (DB queries, export generation, AI calls), consider running the relevant script in [`load-tests/`](./load-tests) against a local or staging environment before merging.
