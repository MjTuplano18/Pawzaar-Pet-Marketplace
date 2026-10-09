package com.pawzaar.pet.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link PetImage}.
 *
 * <p>The derived query names do the work. Two are worth calling out:
 * <ul>
 *   <li>{@link #findByPetIdOrderBySortOrderAsc} - the ordered images for one listing's detail page.</li>
 *   <li>{@link #findByPetIdInAndSortOrder} - fetches the cover (order 0) for a whole page of pets in a
 *       single query, which is how the list endpoint avoids the N+1 problem.</li>
 * </ul>
 */
public interface PetImageRepository extends JpaRepository<PetImage, UUID> {

    List<PetImage> findByPetIdOrderBySortOrderAsc(UUID petId);

    List<PetImage> findByPetIdInAndSortOrder(Collection<UUID> petIds, int sortOrder);

    long countByPetId(UUID petId);

    Optional<PetImage> findByIdAndPetId(UUID id, UUID petId);
}
