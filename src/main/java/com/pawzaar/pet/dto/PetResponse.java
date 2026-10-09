package com.pawzaar.pet.dto;

import com.pawzaar.pet.PetStatus; // Represents the current status of the pet.
import com.pawzaar.pet.Species; // Represents the type/species of the pet.

import java.math.BigDecimal; // Used for accurate monetary values such as pet prices.
import java.time.Instant; // Represents the pet's creation date and time.
import java.util.List; // Holds the listing's images.
import java.util.UUID; // Used for unique identifiers such as pet and seller IDs.


/**
 * Response DTO for retrieving the details of a single pet.
 *
 * Used by GET /api/v1/pets/{id} to return the information
 * needed by the pet detail page.
 *
 * This keeps the API response separate from the database entity,
 * so database changes do not directly affect the API response.
 */
public record PetResponse(

        UUID id,
        UUID sellerId,
        String title,
        Species species,
        String breed,
        int ageMonths,
        BigDecimal price,
        String description,
        String city,
        String province,
        String sex,
        PetStatus status,
        Instant createdAt,
        Instant updatedAt,
        /** All images, ordered: index 0 is the cover. Empty if none uploaded yet. */
        List<PetImageResponse> images

) {}
