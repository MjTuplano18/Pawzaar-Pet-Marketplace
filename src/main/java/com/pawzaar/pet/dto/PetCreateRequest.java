package com.pawzaar.pet.dto;

import com.pawzaar.pet.Species;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/pets}.
 *
 * <p>All constraints are checked by {@code @Valid} in the controller BEFORE the service runs,
 * so the service never sees invalid data.
 */
public record PetCreateRequest(

        @NotBlank(message = "title is required")
        @Size(max = 150, message = "title must be at most 150 characters")
        String title,

        @NotNull(message = "species is required")
        Species species,

        @Size(max = 100, message = "breed must be at most 100 characters")
        String breed,

        @Min(value = 0, message = "ageMonths must be 0 or more")
        @Max(value = 360, message = "ageMonths must be at most 360 (30 years)")
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

        // MALE, FEMALE, or blank/null (optional)
        @Pattern(regexp = "^$|^(MALE|FEMALE)$", message = "sex must be MALE, FEMALE, or omitted")
        String sex

) {}
