-- GOOGLE SIGN-IN: add a nullable google_id column to users so accounts created
-- or linked via Google (POST /api/auth/google) can be re-identified on later
-- logins. Nullable because password-registered users have no Google id.
--
-- google_id can be up to ~30 chars (Google's numeric sub claim), so VARCHAR(64)
-- leaves comfortable headroom. A UNIQUE index enforces one Google account per
-- user id and gives the findByGoogleId lookup an index.
ALTER TABLE users
    ADD COLUMN google_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_id ON users (google_id);