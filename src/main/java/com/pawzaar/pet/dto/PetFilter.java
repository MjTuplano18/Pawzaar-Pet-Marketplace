package com.pawzaar.pet.dto;

import com.pawzaar.pet.Species;

import java.math.BigDecimal;

/**
 * The optional search filters for {@code GET /api/v1/pets}.
 *
 * <p>Every field is nullable: {@code null} means "do not filter on this field".
 * The controller builds one from the query string, and
 * {@link com.pawzaar.pet.repository.PetSpecifications} turns the non-null fields into
 * WHERE clauses.
 */
public record PetFilter(
        Species species,
        String province,
        String city,
        String breed,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minAgeMonths,
        Integer maxAgeMonths
) {
}
