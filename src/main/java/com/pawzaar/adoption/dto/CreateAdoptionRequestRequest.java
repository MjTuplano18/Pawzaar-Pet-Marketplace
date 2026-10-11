package com.pawzaar.adoption.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/pets/{petId}/adoption-requests}.
 * The message is optional; callers may omit it entirely.
 */
public record CreateAdoptionRequestRequest(
        @Size(max = 1000)
        String message
) {
}
