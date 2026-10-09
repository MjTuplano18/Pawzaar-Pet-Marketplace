-- V7: images attached to pet listings.
--
-- One listing can have many images. sort_order 0 is the "cover" shown on cards; the rest follow
-- in order. ON DELETE CASCADE means removing a pet row cleans up its image rows automatically.
--
-- The file bytes themselves live OUTSIDE the database (on disk for now). The row keeps only the
-- opaque storage key plus metadata, which keeps the database small and lets storage move to S3/CDN
-- without a schema change.
CREATE TABLE pet_images (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pet_id       UUID         NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
    storage_key  VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL CHECK (size_bytes > 0),
    sort_order   INTEGER      NOT NULL CHECK (sort_order >= 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One index serves both "all images of a pet, in order" and the cover lookup (pet_id, 0).
CREATE INDEX idx_pet_images_pet ON pet_images (pet_id, sort_order);
