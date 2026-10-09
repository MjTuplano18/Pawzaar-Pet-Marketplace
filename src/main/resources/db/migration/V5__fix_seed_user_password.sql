-- Fix the sample seller's password (Step 6 review follow-up).
--
-- WHY A NEW FILE AND NOT AN EDIT OF V3:
-- Flyway migrations are APPEND-ONLY. Once V3 ran on any database, its checksum was recorded;
-- editing V3 afterwards makes every existing database fail validation with a checksum mismatch.
-- (V3 was temporarily edited during development and has been restored.) All corrections to
-- already-applied migrations go in a new file like this one.
--
-- The V3 seed stored the placeholder 'not-a-real-hash', which is not a valid BCrypt hash, so
-- seed@pawzaar.test could never log in. This sets a real BCrypt hash of the password
-- 'pawzaar123' (the same password used by the other dev seed account).
UPDATE users
SET password_hash = '{bcrypt}$2a$10$muhvoq2wCyg9woryy3fvKeDuw6iAgTu83tMP7ctaGLdB217w7ku5W'
WHERE email = 'seed@pawzaar.test';
