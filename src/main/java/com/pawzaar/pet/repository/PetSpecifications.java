package com.pawzaar.pet.repository;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.dto.PetFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WHERE-clause fragments for the pet feed, built as JPA Specifications.
 *
 * <p>A Specification is a lambda that Spring hands the Criteria API pieces so you can add
 * predicates conditionally, instead of writing one query method per filter combination
 * (which would be 2^n methods for n optional filters).
 */
public final class PetSpecifications {

    /**
     * Escape character for LIKE: {@code \} in the SQL, chosen because it is rarely in a place or breed name.
     */
    private static final char LIKE_ESCAPE = '\\';

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
                // Case-insensitive contains: "metro" matches "Metro Manila". M3: the user's % and
                // _ are escaped so they match literally instead of acting as wildcards.
                predicates.add(contains(cb, cb.lower(root.get("province")), filter.province()));
            }
            if (filter.city() != null) {
                predicates.add(contains(cb, cb.lower(root.get("city")), filter.city()));
            }
            if (filter.breed() != null) {
                predicates.add(contains(cb, cb.lower(root.get("breed")), filter.breed()));
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

    /**
     * Case-insensitive contains for a text filter: lower(column) LIKE %term%. Used by
     * province, city and breed, so a fragment like "quezon" still finds "Quezon City".
     */
    private static Predicate contains(CriteriaBuilder cb, Expression<String> column, String term) {
        String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        return cb.like(column, pattern, LIKE_ESCAPE);
    }

    /**
     * Escapes the LIKE metacharacters so a search term is matched literally (M3). Without this a
     * client typing {@code %} would match every row - a wildcard they never asked for, and a cheap
     * way to scan the whole table. Backslash must be escaped first, or it would double-escape the
     * escapes we add next.
     */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
