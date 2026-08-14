-- SEC FIX: email_otp now stores the SHA-256 hex digest (64 chars) of the OTP
-- instead of the plaintext OTP (VARCHAR(20)). Widen the column so the digest
-- fits. Existing rows contain short plaintext values that are still < 64 chars,
-- so no truncation occurs; those old plaintext OTPs expire naturally and are
-- overwritten by digests on the next resend/registration.
ALTER TABLE users MODIFY COLUMN email_otp VARCHAR(64) NULL;
