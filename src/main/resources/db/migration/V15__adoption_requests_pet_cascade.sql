-- V15: make adoption_requests follow their pet on hard delete.
--
-- Deletes used to be soft (the service only set status = HIDDEN), so the FK could stay
-- RESTRICT. Deletes are now permanent: a listing that is gone leaves adoption requests
-- referencing nothing, so they must be removed with it instead of blocking the delete.
--
-- The storage file for each pet image is deleted separately AFTER the commit (files are
-- not rows, so ON DELETE CASCADE cannot remove them) — see PetImageService.deleteStorageForPet.

ALTER TABLE adoption_requests
    DROP CONSTRAINT adoption_requests_pet_id_fkey;

ALTER TABLE adoption_requests
    ADD CONSTRAINT adoption_requests_pet_id_fkey
    FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE CASCADE;
