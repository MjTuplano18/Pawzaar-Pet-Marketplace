-- Password reset (new feature).
--
-- Same shape and at-rest rule as email_verification_tokens (V12): only SHA-256(rawToken) is stored,
-- so a leak of this table reveals nothing usable. A token is single-use and short-lived; `used` is
-- flipped by a conditional UPDATE so two requests racing on the same link cannot both succeed.
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_hash ON password_reset_tokens (token_hash);
