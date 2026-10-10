-- V11 (H4): make a pet's image order unambiguous.
--
-- WHY: 0 is the "cover" (the card image) and the detail page renders images by ascending
-- sort_order. The old uploader used count() as the next order, so once an earlier image was
-- deleted the orders were non-dense (e.g. 0 and 2) and the next upload reused 2 - two rows with
-- the same order, and no row at all claiming the cover.
--
-- The application now uses max+1 and renumbers survivors on delete. This migration makes the
-- database itself guarantee the invariant.
--
-- 1) Renumber any existing rows that already collide, deterministically, BEFORE adding the
--    constraint - otherwise the ALTER would fail on a dirty database. row_number() - 1 gives a
--    dense 0..n-1 per pet, so the lowest existing order stays the cover.
WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY pet_id ORDER BY sort_order, created_at, id) - 1 AS rn
    FROM pet_images
)
UPDATE pet_images pi
SET sort_order = ranked.rn
FROM ranked
WHERE pi.id = ranked.id
  AND pi.sort_order IS DISTINCT FROM ranked.rn;

-- 2) Enforce one order per pet.
ALTER TABLE pet_images
    ADD CONSTRAINT uq_pet_images_pet_sort_order UNIQUE (pet_id, sort_order);

-- 3) The old non-unique index covered exactly (pet_id, sort_order); the UNIQUE constraint above
--    created an index on the same columns, so the old one is now redundant.
DROP INDEX IF EXISTS idx_pet_images_pet;
