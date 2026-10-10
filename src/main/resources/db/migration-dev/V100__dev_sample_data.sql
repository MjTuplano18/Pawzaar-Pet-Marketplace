-- Dev-only sample data (C1).
--
-- Applied ONLY when the dev profile is active: application-dev.yaml adds
-- classpath:db/migration-dev to spring.flyway.locations. Production and the test suite
-- use the default single location (db/migration), so these rows can never reach a prod
-- or test database. The old V3 seed used to live among the real migrations - see V10.
--
-- Same content as the original V3/V5 seed, including the real BCrypt hash of the dev
-- password so seed@pawzaar.test can log in locally.

INSERT INTO users (id, email, password_hash, display_name, role)
VALUES ('11111111-1111-1111-1111-111111111111', 'seed@pawzaar.test',
        '{bcrypt}$2a$10$muhvoq2wCyg9woryy3fvKeDuw6iAgTu83tMP7ctaGLdB217w7ku5W',
        'Sample Seller', 'SELLER');

INSERT INTO pets (seller_id, title, species, breed, age_months, price, description, city, province)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Friendly Golden Retriever puppy', 'DOG', 'Golden Retriever', 3, 15000.00, 'Vaccinated, dewormed, playful.', 'Meycauayan', 'Bulacan'),
    ('11111111-1111-1111-1111-111111111111', 'Shih Tzu, 1 year old', 'DOG', 'Shih Tzu', 12, 9000.00, 'House trained and gentle.', 'Quezon City', 'Metro Manila'),
    ('11111111-1111-1111-1111-111111111111', 'Persian kitten', 'CAT', 'Persian', 2, 12000.00, 'Litter trained, with papers.', 'Angeles', 'Pampanga');