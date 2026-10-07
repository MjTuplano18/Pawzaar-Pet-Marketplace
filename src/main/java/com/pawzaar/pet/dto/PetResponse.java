package com.pawzaar.pet.dto;

import com.pawzaar.pet.PetStatus; // Represents the current status of the pet.
import com.pawzaar.pet.Species; // Represents the type/species of the pet.

import java.math.BigDecimal; // Used for accurate monetary values such as pet prices.
import java.time.Instant; // Represents the pet's creation date and time.
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

        // Unique identifier of the pet.
        UUID id,

        // Unique identifier of the seller who listed the pet.
        UUID sellerId,

        // Title displayed for the pet listing.
        String title,

        // Species of the pet, such as DOG or CAT.
        Species species,

        // Breed of the pet.
        String breed,

        // Age of the pet in months.
        int ageMonths,

        // Asking price of the pet.
        BigDecimal price,

        // Detailed description of the pet.
        String description,

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