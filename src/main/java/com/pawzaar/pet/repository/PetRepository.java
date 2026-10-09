package com.pawzaar.pet.repository;

import com.pawzaar.pet.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import com.pawzaar.pet.PetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/**
 * Data layer of the pet feature. On startup Spring Data reads the type arguments
 * (&lt;Pet, UUID&gt;), generates an implementation of this interface in memory, and
 * registers it as a bean (that's the "Bootstrapping Spring Data JPA repositories"
 * line in the log).
 *
 * <p>Inherited without writing any code: findAll(Pageable), findById(UUID),
 * save(...), deleteById(...), count()...
 *
 * <p>JpaSpecificationExecutor adds findAll(Specification, Pageable): the dynamic
 * WHERE-clause queries used by search (see PetSpecifications).
 */
public interface PetRepository extends JpaRepository<Pet, UUID>,
                                        JpaSpecificationExecutor<Pet> {

    Page<Pet> findByStatus(PetStatus status, Pageable pageable);

    Optional<Pet> findByIdAndStatus(UUID id, PetStatus status);

    // "My Listings" - all statuses for the owner's dashboard
    Page<Pet> findBySellerId(UUID sellerId, Pageable pageable);
}