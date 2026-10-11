-- V14: adoption_requests table
-- Tracks requests from a potential adopter to a pet owner.

CREATE TABLE adoption_requests (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    pet_id        UUID        NOT NULL REFERENCES pets(id),
    requester_id  UUID        NOT NULL REFERENCES users(id),
    message       TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Speed up "show all requests for this pet" (owner inbox)
CREATE INDEX idx_adoption_requests_pet_id ON adoption_requests(pet_id);

-- Speed up "show all requests I made" (requester dashboard)
CREATE INDEX idx_adoption_requests_requester_id ON adoption_requests(requester_id);

-- Prevent a requester from having more than one active (PENDING) request per pet
CREATE UNIQUE INDEX idx_adoption_requests_unique_active
    ON adoption_requests(pet_id, requester_id)
    WHERE status = 'PENDING';
