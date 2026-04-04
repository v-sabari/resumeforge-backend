# ResumeForge AI — Backend Deployment Guide
> Last updated: April 2025 | Production Hardening Pass Applied

---

## Quick Start Checklist

- [ ] Provision a PostgreSQL database (Render → New → PostgreSQL)
- [ ] Copy `.env.example` → fill all values on Render (Environment tab)
- [ ] **First deploy only**: temporarily set `spring.jpa.hibernate.ddl-auto=create` to create tables
- [ ] After first deploy confirms DB tables exist: change back to `validate` (already set in `application.properties`)
- [ ] Set up Razorpay webhook (instructions below)
- [ ] Add `internal_payment_id` to your Razorpay Payment Link notes field
- [ ] Test payment flow end-to-end in Razorpay test mode
- [ ] Verify `/api/health` returns `{"status":"UP"}`

---

## Environment Variables (Render → Your Service → Environment)

| Variable | Where to get it | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Render PostgreSQL → Connect → External URL | `jdbc:postgresql://host:5432/dbname` |
| `SPRING_DATASOURCE_USERNAME` | Render PostgreSQL → Connect | Your DB username |
| `SPRING_DATASOURCE_PASSWORD` | Render PostgreSQL → Connect | Your DB password |
| `FRONTEND_URL` | Your Vercel URL, no trailing slash | `https://resumeforge.ai` |
| `APP_JWT_SECRET` | Run: `openssl rand -hex 32` | Must be ≥64 chars. Never expose. |
| `RAZORPAY_KEY_ID` | Razorpay Dashboard → Settings → API Keys | `rzp_live_XXXXXXXX` |
| `RAZORPAY_KEY_SECRET` | Razorpay Dashboard → Settings → API Keys | Keep secret — never in frontend |
| `RAZORPAY_PAYMENT_LINK` | Razorpay Dashboard → Payment Links | `https://rzp.io/rzp/XXXXXXXX` |
| `RAZORPAY_WEBHOOK_SECRET` | Razorpay Dashboard → Webhooks → your endpoint → Secret field | Required for HMAC verification |

---

## Deploying to Render

```
1. Push backend code to GitHub
2. Render Dashboard → New → Web Service → connect repo
3. Runtime: Docker (uses Dockerfile in repo root)
4. Set all environment variables in Environment tab
5. Health Check Path: /api/health
6. Click Deploy
```

### First Deploy — Database Tables

On your very first deploy, Hibernate needs to create tables:

```
# In Render → Environment, temporarily add:
SPRING_JPA_HIBERNATE_DDL_AUTO=create

# Wait for deploy to succeed and tables to be created
# Then immediately change back to:
SPRING_JPA_HIBERNATE_DDL_AUTO=validate

# Or use a SQL migration tool (Flyway recommended for long-term)
```

---

## Razorpay Webhook Setup (CRITICAL)

This is what activates premium after payment. Without this, premium NEVER activates.

```
1. Razorpay Dashboard → Settings → Webhooks → Add New Webhook
2. Webhook URL: https://your-backend.onrender.com/api/webhooks/razorpay
3. Secret: generate a strong secret → copy it → set as RAZORPAY_WEBHOOK_SECRET env var
4. Events to enable:
   ✅ payment.captured
   ✅ order.paid
5. Save → test with a test payment
```

---

## Razorpay Payment Link — Notes Field (CRITICAL for payment matching)

The webhook identifies which user to upgrade by matching the payment ID.
Because Razorpay Payment Links generate their own payment IDs (different from ours),
you MUST embed our internal payment ID in the Payment Link's notes field.

**How to set notes on a Payment Link:**

Option A — Razorpay Dashboard:
```
Payment Links → your link → Edit → Notes → Add:
  Key: internal_payment_id
  Value: (this will be dynamically set per user — use the API instead)
```

Option B — Razorpay API (recommended for production):
```java
// When creating a payment link via Razorpay API, add:
{
  "notes": {
    "internal_payment_id": "pay_abc123..." // your DB payment ID
  }
}
```

> **Easiest short-term fix**: Switch to Razorpay Orders API instead of Payment Links.
> This gives you full control over the order/payment ID lifecycle.

---

## Security Notes

### JWT Secret
- Must be at least 32 bytes (64+ hex chars recommended)
- Generate: `openssl rand -hex 32`
- Never commit to Git. Set only in Render environment.

### Webhook Secret
- Set `RAZORPAY_WEBHOOK_SECRET` — without it, signature verification is skipped in dev mode
- In production: if this is blank, ALL webhook calls are accepted — a major security hole
- Verify it is set: check Render logs for `RAZORPAY_WEBHOOK_SECRET not configured` warning on startup

### Critical Security Fixes Applied (April 2025)
- `PaymentService.verify()` — removed premium self-activation; webhook-only now
- `PremiumService.activate()` — added payment status check; PAID required before upgrade
- `application.properties` — `ddl-auto=update` → `validate`; `include-message=always` → `never`

---

## Health Check

```bash
curl https://your-backend.onrender.com/api/health
# Expected: {"status":"UP","service":"ResumeForge AI Backend","timestamp":"..."}
```

---

## Render Free Tier Notes

- Services spin down after **15 minutes** of inactivity
- Cold start takes **30–60 seconds** — users may see a slow first load
- Frontend Axios timeout is set to 30s — handles cold starts
- **Fix**: Add UptimeRobot (free) to ping `/api/health` every 10 minutes, keeping it warm
- **Better fix**: Upgrade to Render Starter ($7/month) for always-on

---

## Database Backup

Render PostgreSQL free tier does NOT auto-backup.
Before launch, enable daily backups:
```
Render Dashboard → your PostgreSQL → Backups → Enable
```
Or upgrade to a paid tier which includes automatic backups.

---

## Post-Deploy Verification

```
✅ GET  /api/health → {"status":"UP"}
✅ POST /api/auth/register → creates user, returns JWT token
✅ POST /api/auth/login → returns JWT token
✅ GET  /api/auth/me (with Bearer token) → returns user object
✅ POST /api/resumes (with Bearer token) → creates resume
✅ GET  /api/premium/status (with Bearer token) → {"isPremium":false}
✅ POST /api/webhooks/razorpay — test with Razorpay dashboard test webhook
✅ GET  /api/export/status (with Bearer token) → export counts correct
```

---

## Monitoring After Launch

- Render Dashboard → Logs — watch for 5xx errors and exceptions
- Razorpay Dashboard → Webhooks → Delivery — ensure webhooks succeed (200 response)
- Monitor for `RAZORPAY_WEBHOOK_SECRET not configured` in logs — means secret is missing
- Monitor for `No payment found for razorpay id` in logs — means payment ID matching is broken
- Set up email alerts in Render for service crashes
