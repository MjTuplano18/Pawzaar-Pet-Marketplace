-- M4d: email verification.
--
-- Proves the person controls the address they registered with. The users.verified column already
-- exists (see V2); this table holds the one-time tokens that flip it.
--
-- Same at-rest rule as refresh_tokens (V4): only SHA-256(rawToken) is stored, so a leak of this
-- table reveals nothing usable. A token is single-use and short-lived; `used` is flipped by a
-- conditional UPDATE so two requests racing on the same link cannot both succeed.
CREATE TABLE email_verification_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_hash ON email_verification_tokens (token_hash);
