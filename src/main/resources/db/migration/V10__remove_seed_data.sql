-- C1: the developer seed account must not ship to production.
--
-- WHY A NEW FILE AND NOT AN EDIT OF V3/V5:
-- Flyway migrations are append-only. Once V3/V5 ran on any database their checksums were
-- recorded; editing them now would break validation on every existing database. So the seed
-- is removed FORWARD, in a new migration, exactly like V5 fixed V3's hash.
--
-- The sample data itself is not lost: it moved to a DEV-ONLY Flyway location
-- (db/migration-dev), which application-dev.yaml adds for the dev profile only. Production
-- and the test suite keep the default single location and therefore get NO sample rows.
--
-- Order matters: pets reference the seller (FK), so the seller's pets go first.
DELETE FROM pets  WHERE seller_id = '11111111-1111-1111-1111-111111111111';
DELETE FROM users WHERE id        = '11111111-1111-1111-1111-111111111111';