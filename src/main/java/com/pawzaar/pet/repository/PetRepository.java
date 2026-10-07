package com.pawzaar.pet.repository;

import com.pawzaar.pet.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Data layer of the pet feature. On startup Spring Data reads the type arguments
 * (&lt;Pet, UUID&gt;), generates an implementation of this interface in memory, and
 * registers it as a bean (that's the "Bootstrapping Spring Data JPA repositories"
 * line in the log).
 *
 * <p>Inherited without writing any code: findAll(Pageable), findById(UUID),
 * save(...), deleteById(...), count()...
 */
public interface PetRepository extends JpaRepository<Pet, UUID> {
}