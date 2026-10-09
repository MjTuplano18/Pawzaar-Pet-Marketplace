package com.pawzaar.pet.repository;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.dto.PetFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * WHERE-clause fragments for the pet feed, built as JPA Specifications.
 *
 * <p>A Specification is a lambda that Spring hands the Criteria API pieces so you can add
 * predicates conditionally, instead of writing one query method per filter combination
 * (which would be 2^n methods for n optional filters).
 */
public final class PetSpecifications {

    private PetSpecifications() {
    }

    /**
     * ACTIVE listings that also satisfy every non-null field of {@code filter}.
     *
     * <p>The lambda receives three things:
     * <ul>
     *   <li>{@code root} - the pets table; {@code root.get("price")} is a column reference,</li>
     *   <li>{@code query} - the whole query (used rarely, e.g. to de-duplicate joins),</li>
     *   <li>{@code cb} - the CriteriaBuilder, a factory for predicates (equal, like, >= ...).</li>
     * </ul>
     */
    public static Specification<Pet> activeMatching(PetFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Never leak non-ACTIVE listings - this clause is always present.
            predicates.add(cb.equal(root.get("status"), PetStatus.ACTIVE));

            // Each filter is added ONLY when the client supplied it.
            if (filter.species() != null) {
                predicates.add(cb.equal(root.get("species"), filter.species()));
            }
            if (filter.province() != null) {
                predicates.add(cb.equal(root.get("province"), filter.province()));
            }
            if (filter.city() != null) {
                predicates.add(cb.equal(root.get("city"), filter.city()));
            }
            if (filter.breed() != null) {
                // Case-insensitive "contains": lower(breed) LIKE %term%.
                predicates.add(cb.like(cb.lower(root.get("breed")),
                        "%" + filter.breed().toLowerCase() + "%"));
            }
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
            }
            if (filter.minAgeMonths() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("ageMonths"), filter.minAgeMonths()));
            }
            if (filter.maxAgeMonths() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("ageMonths"), filter.maxAgeMonths()));
            }

            // AND every clause together into one WHERE.
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
