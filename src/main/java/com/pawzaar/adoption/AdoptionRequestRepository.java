package com.pawzaar.adoption;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Data layer for adoption requests.
 * All queries are derived from Spring Data method names — no SQL written here.
 */
public interface AdoptionRequestRepository extends JpaRepository<AdoptionRequest, UUID> {

    /** All requests targeting a specific pet (used by the pet owner's inbox). */
    List<AdoptionRequest> findByPetId(UUID petId);

    /** All requests made by a specific user (the requester's own history). */
    List<AdoptionRequest> findByRequesterId(UUID requesterId);

    /**
     * Guard against duplicate PENDING requests.
     * Returns {@code true} if a PENDING request already exists for the given pet+requester pair.
     */
    boolean existsByPetIdAndRequesterIdAndStatus(UUID petId, UUID requesterId, AdoptionStatus status);
}
