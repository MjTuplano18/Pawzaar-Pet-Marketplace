package com.pawzaar.adoption.dto;

import com.pawzaar.adoption.AdoptionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/adoption-requests/{id}}.
 * The caller supplies the desired target status;
 * {@link com.pawzaar.adoption.AdoptionService} enforces which transitions are allowed.
 */
public record UpdateAdoptionStatusRequest(
        @NotNull
        AdoptionStatus status
) {
}
