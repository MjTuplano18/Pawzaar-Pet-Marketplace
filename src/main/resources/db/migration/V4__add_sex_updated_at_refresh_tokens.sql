-- Add sex column to pets (optional field; existing rows get NULL)
ALTER TABLE pets ADD COLUMN sex VARCHAR(10);

-- Add updated_at for optimistic-locking baseline; backfill with created_at
ALTER TABLE pets ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE pets SET updated_at = created_at;

-- version column used by JPA @Version for optimistic locking
ALTER TABLE pets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Refresh tokens: short-lived, hashed, rotated on use, revocable on logout.
-- token_hash stores SHA-256(rawToken) so the plain token is never at rest.
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_hash   ON refresh_tokens (token_hash);
