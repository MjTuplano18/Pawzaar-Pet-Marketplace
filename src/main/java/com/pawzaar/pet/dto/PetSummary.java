package com.pawzaar.pet.dto;

import com.pawzaar.pet.PetStatus; // Represents the current status of the pet listing.
import com.pawzaar.pet.Species; // Represents the type/species of the pet.

import java.math.BigDecimal; // Used for accurate monetary values such as pet prices.
import java.time.Instant; // Represents the date and time when the listing was created.
import java.util.UUID; // Used for the unique identifier of the pet.


/**
 * Response DTO for the pet listing endpoint.
 *
 * Used by GET /api/v1/pets to provide the information
 * needed to display each pet as a listing card.
 *
 * This response contains only the basic information needed
 * for the listing page instead of the full pet details.
 */
public record PetSummary(

        // Unique identifier of the pet.
        UUID id,

        // Title displayed on the pet listing card.
        String title,

        // Species of the pet, such as DOG or CAT.
        Species species,

        // Breed of the pet.
        String breed,

        // Asking price of the pet.
        BigDecimal price,

        // City where the pet is located.
        String city,

        // Province where the pet is located.
        String province,

        // Current status of the pet listing.
        PetStatus status,

        // Date and time when the listing was created.
        Instant createdAt

) {
}