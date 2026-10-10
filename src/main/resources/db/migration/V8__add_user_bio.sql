-- Step 13b: users can add a short "about me" bio to their profile.
-- Optional, like phone: NULL until the user writes something. Matches the entities'
-- @Column(length = 500), which ddl-auto:validate checks against this exact length.
ALTER TABLE users ADD COLUMN bio VARCHAR(500);