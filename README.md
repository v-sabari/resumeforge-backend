# ResumeForge AI Backend

Production-grade Spring Boot backend for **ResumeForge AI**, an AI-powered resume builder SaaS.

## Stack

- Spring Boot 3
- Spring Security
- JWT Authentication
- Spring Data JPA / Hibernate
- MySQL
- Maven

## Features

- JWT-based authentication
- Resume CRUD with nested experience, education, and projects
- Rule-based free AI endpoints for summaries, bullets, skills, and rewrites
- Ad-gated free export flow
- Strict export access control
- Premium payment flow with Razorpay placeholders
- Premium activation and payment history
- Global exception handling and CORS config

## Business Rules Implemented

- Users get **2 free exports only**
- **Each free export requires ad completion**
- If ad is skipped or fails, export is blocked
- After 2 exports, payment is required
- After premium activation, exports become unlimited with no ad requirement

## Project Structure

```text
src/main/java/com/resumeforge/ai/
  controller/
  service/
  repository/
  entity/
  dto/
  security/
  config/
  exception/
  util/
```

## Environment Configuration

Copy `.env.example` values into your environment or configure them in your IDE / shell:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `FRONTEND_URL`
- `RAZORPAY_KEY_ID`
- `RAZORPAY_KEY_SECRET`
- `RAZORPAY_PAYMENT_LINK`

`application.properties` already reads these values through Spring placeholders.

## MySQL Setup

1. Start MySQL.
2. Create a database if needed:

```sql
CREATE DATABASE resumeforge_ai;
```

3. Update datasource credentials.
4. Optional: run `schema.sql` manually if you want a fixed initial schema. With `spring.jpa.hibernate.ddl-auto=update`, the application can also create/update tables automatically.

## Run Locally

```bash
mvn spring-boot:run
```

The server runs by default on:

```text
http://localhost:8080
```

## Build

```bash
mvn clean package
```

Generated jar:

```text
target/resume-forge-ai-backend-1.0.0.jar
```

## Authentication

### Register
`POST /api/auth/register`

Request:

```json
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "StrongPass123"
}
```

### Login
`POST /api/auth/login`

Request:

```json
{
  "email": "jane@example.com",
  "password": "StrongPass123"
}
```

Both return a JWT token and user object. Pass the token in the `Authorization` header:

```text
Authorization: Bearer <jwt>
```

## API Overview

### Auth
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

### Resumes
- `GET /api/resumes`
- `POST /api/resumes`
- `GET /api/resumes/{id}`
- `PUT /api/resumes/{id}`
- `DELETE /api/resumes/{id}`

### AI
- `POST /api/ai/summary`
- `POST /api/ai/bullets`
- `POST /api/ai/skills`
- `POST /api/ai/rewrite`

### Ads
- `POST /api/ads/start`
- `POST /api/ads/complete`
- `POST /api/ads/fail`

### Payments
- `POST /api/payments/create`
- `POST /api/payments/verify`
- `GET /api/payments/history`

### Premium
- `GET /api/premium/status`
- `POST /api/premium/activate`

### Export
- `POST /api/export/check-access`
- `POST /api/export/record`
- `GET /api/export/status`

## Payment Flow

### Create payment
`POST /api/payments/create`

Creates a payment record and returns:
- internal `paymentId`
- configured Razorpay payment link
- configured Razorpay key id

### Verify payment
`POST /api/payments/verify`

MVP behavior:
- accepts payment verification payload
- marks payment as `PAID` or `VERIFIED`
- activates premium when verification succeeds

Production note:
- integrate Razorpay webhook or signature verification using `RAZORPAY_KEY_SECRET`
- tighten verification logic around `razorpayPaymentId` and `razorpaySignature`

## Export Flow

1. Frontend calls `POST /api/export/check-access`
2. If free user has no unlocked export, frontend starts ad flow via `POST /api/ads/start`
3. On successful ad completion, frontend calls `POST /api/ads/complete`
4. Backend then allows one export
5. After actual export generation, frontend calls `POST /api/export/record`
6. After 2 free exports, backend returns payment-required state

## AI Notes

No paid AI APIs are used. The AI layer is fully rule-based and optimized for:
- ATS-friendly wording
- concise one-page resume content
- stronger action-oriented bullet writing
- clearer, more professional rewrite suggestions

## Deployment Notes

- Configure real MySQL in production
- Set a strong random `JWT_SECRET`
- Restrict `FRONTEND_URL` to your production domain
- Replace Razorpay placeholders with real values
- Prefer `ddl-auto=validate` in hardened production deployments
- Add webhook verification for production payment confirmation
