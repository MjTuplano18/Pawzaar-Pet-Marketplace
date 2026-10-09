package com.pawzaar.pet.dto;

import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.Species;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight DTO for the listing page ({@code GET /api/v1/pets}).
 * Contains only the fields needed to render a pet card; omits the full description
 * and seller details that belong on the detail page.
 */
public record PetSummary(
        UUID id,
        String title,
        Species species,
        String breed,
        BigDecimal price,
        String city,
        String province,
        String sex,
        PetStatus status,
        Instant createdAt,
        /** URL of the cover image (sort order 0), or null when the listing has no images yet. */
        String coverImageUrl
) {}
