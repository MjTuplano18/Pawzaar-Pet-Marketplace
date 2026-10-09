package com.pawzaar.pet.dto;

import com.pawzaar.pet.PetStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code PUT /api/v1/pets/{id}}.
 *
 * <p>This is a FULL replacement (PUT semantics): every field must be supplied.
 * A PATCH with partial fields can be added later when the need arises.
 */
public record PetUpdateRequest(

        @NotBlank(message = "title is required")
        @Size(max = 150, message = "title must be at most 150 characters")
        String title,

        @Size(max = 100, message = "breed must be at most 100 characters")
        String breed,

        @Min(value = 0, message = "ageMonths must be 0 or more")
        @Max(value = 360, message = "ageMonths must be at most 360")
        int ageMonths,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price must be 0 or more")
        BigDecimal price,

        @Size(max = 5000, message = "description must be at most 5000 characters")
        String description,

        @NotBlank(message = "city is required")
        @Size(max = 100, message = "city must be at most 100 characters")
        String city,

        @NotBlank(message = "province is required")
        @Size(max = 100, message = "province must be at most 100 characters")
        String province,

        @Pattern(regexp = "^$|^(MALE|FEMALE)$", message = "sex must be MALE, FEMALE, or omitted")
        String sex,

        // Sellers may set ACTIVE, SOLD, or HIDDEN. PENDING_REVIEW is rejected by PetService
        // (it is an administrator-only status), which returns 400.
        @NotNull(message = "status is required")
        PetStatus status

) {}
