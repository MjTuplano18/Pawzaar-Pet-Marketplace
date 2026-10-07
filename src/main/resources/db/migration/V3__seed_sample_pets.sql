INSERT INTO users (id, email, password_hash, display_name, role)
VALUES ('11111111-1111-1111-1111-111111111111', 'seed@pawzaar.test',
        'not-a-real-hash', 'Sample Seller', 'SELLER');

INSERT INTO pets (seller_id, title, species, breed, age_months, price, description, city, province)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Friendly Golden Retriever puppy', 'DOG', 'Golden Retriever', 3, 15000.00, 'Vaccinated, dewormed, playful.', 'Meycauayan', 'Bulacan'),
    ('11111111-1111-1111-1111-111111111111', 'Shih Tzu, 1 year old', 'DOG', 'Shih Tzu', 12, 9000.00, 'House trained and gentle.', 'Quezon City', 'Metro Manila'),
    ('11111111-1111-1111-1111-111111111111', 'Persian kitten', 'CAT', 'Persian', 2, 12000.00, 'Litter trained, with papers.', 'Angeles', 'Pampanga');