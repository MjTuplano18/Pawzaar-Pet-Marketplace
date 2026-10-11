package com.pawzaar.adoption.dto;

import com.pawzaar.adoption.AdoptionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model returned to the client for any adoption request endpoint.
 * Mirrors the {@link com.pawzaar.adoption.AdoptionRequest} entity,
 * enriched with human-readable names from related entities.
 */
public record AdoptionRequestResponse(
        UUID id,
        UUID petId,
        String petTitle,
        UUID requesterId,
        String requesterDisplayName,
        String message,
        AdoptionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
