-- Step 13c: a user may have exactly ONE profile picture (avatar).
-- The storage key is opaque (a random UUID + extension) and is NEVER exposed via the API;
-- clients only ever see the derived URL /api/v1/me/avatar. The MIME type is stored beside the
-- key so serving can set the correct Content-Type without guessing.
ALTER TABLE users ADD COLUMN avatar_storage_key   VARCHAR(255);
ALTER TABLE users ADD COLUMN avatar_content_type  VARCHAR(50);