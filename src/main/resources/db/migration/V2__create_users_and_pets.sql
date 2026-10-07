CREATE TABLE users (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       display_name  VARCHAR(100) NOT NULL,
                       phone         VARCHAR(30),
                       role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
                       verified      BOOLEAN      NOT NULL DEFAULT FALSE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE pets (
                      id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                      seller_id   UUID           NOT NULL REFERENCES users(id),
                      title       VARCHAR(150)   NOT NULL,
                      species     VARCHAR(20)    NOT NULL,
                      breed       VARCHAR(100),
                      age_months  INTEGER        NOT NULL CHECK (age_months >= 0),
                      price       NUMERIC(10,2)  NOT NULL CHECK (price >= 0),
                      description TEXT,
                      city        VARCHAR(100)   NOT NULL,
                      province    VARCHAR(100)   NOT NULL,
                      status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
                      created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_pets_status_created ON pets (status, created_at DESC);
CREATE INDEX idx_pets_species_breed  ON pets (species, breed);
CREATE INDEX idx_pets_location       ON pets (province, city);
CREATE INDEX idx_pets_price          ON pets (price);
CREATE INDEX idx_pets_seller         ON pets (seller_id);
CREATE INDEX idx_pets_title_trgm     ON pets USING gin (title gin_trgm_ops);