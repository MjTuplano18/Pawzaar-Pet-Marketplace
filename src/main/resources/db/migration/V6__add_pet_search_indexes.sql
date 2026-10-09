-- V6: indexes for the search filters and sorts added in Step 10 (GET /api/v1/pets).
--
-- Indexes already exist from V2, so we do NOT recreate them:
--   status + created_at  -> idx_pets_status_created   (default feed + sort)
--   species (+breed)     -> idx_pets_species_breed    (species filter)
--   province (+city)     -> idx_pets_location         (province filter)
--   price                -> idx_pets_price            (price range/sort)
--
-- Only the genuinely missing access paths are added here.

-- 1) Age filter and age sort (minAgeMonths / maxAgeMonths / ORDER BY age_months).
CREATE INDEX idx_pets_age_months ON pets (age_months);

-- 2) City on its own. A B-tree is only usable from its LEADING column, so the existing
--    (province, city) composite cannot serve a city-only filter. This index covers it.
CREATE INDEX idx_pets_city ON pets (city);

-- 3) Breed search. Two reasons a plain B-tree can't help:
--    (a) the filter is case-insensitive, so we query lower(breed);
--    (b) it is a CONTAINS search (LIKE '%term%'), which no B-tree can serve.
--    A GIN trigram index on the exact expression lower(breed) is the fix. pg_trgm is
--    already enabled in V1. Note: trigram search is only effective for terms >= 3 chars.
CREATE INDEX idx_pets_breed_trgm ON pets USING gin (lower(breed) gin_trgm_ops);
