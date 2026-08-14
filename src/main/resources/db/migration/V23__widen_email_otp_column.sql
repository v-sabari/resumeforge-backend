-- PostgreSQL syntax (target database is Neon PostgreSQL 17.x).
--
-- SEC FIX: email_otp now stores the SHA-256 hex digest (64 chars) of the OTP
-- instead of the plaintext OTP (VARCHAR(20)). Widen the column so the digest
-- fits. Existing rows contain short plaintext values that are still < 64 chars,
-- so no truncation occurs; those old plaintext OTPs expire naturally and are
-- overwritten by digests on the next resend/registration.
--
-- PostgreSQL has no MODIFY COLUMN; widening a column's type is a single
-- ALTER COLUMN ... TYPE clause. email_otp is already nullable (no NOT NULL
-- was set on it in V1__initial_schema.sql), so no separate nullability
-- change is needed.
ALTER TABLE users
    ALTER COLUMN email_otp TYPE VARCHAR(64);
