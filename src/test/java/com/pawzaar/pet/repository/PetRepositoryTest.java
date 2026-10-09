package com.pawzaar.pet.repository;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository slice test: boots JPA + the real PostgreSQL in Docker,
 * but replaces nothing - it queries the ACTUAL seed data from V3.
 *
 * replace = NONE is required because there is no in-memory H2 on the classpath;
 * instead we point at the Docker database. Each test runs in a transaction that is
 * rolled back afterwards, so the seed data is never polluted.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PetRepositoryTest {

    @Autowired
    private PetRepository petRepository;

    @Test
    void paginationCountsAllSeedPets() {
        Page<Pet> page = petRepository.findAll(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        // V3 seeded exactly 3 pets: page 0 holds 2, total pages = ceil(3/2) = 2.
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void findByIdWithRealSeedIdReturnsPet() {
        Pet anyPet = petRepository.findAll(PageRequest.of(0, 1)).getContent().get(0);

        var found = petRepository.findById(anyPet.getId());

        assertTrue(found.isPresent());
        assertFalse(found.get().getTitle().isBlank());
        assertNotNull(found.get().getSellerId());   // V3 links every pet to the seeded seller
    }

    @Test
    void findByStatusReturnsOnlyActivePets() {
        // V3 seeded 3 pets, all created with the table default status ACTIVE.
        Page<Pet> active = petRepository.findByStatus(PetStatus.ACTIVE, PageRequest.of(0, 10));

        assertEquals(3, active.getTotalElements());
        assertTrue(active.getContent().stream().allMatch(p -> p.getStatus() == PetStatus.ACTIVE));
    }

    @Test
    void findByIdAndStatusHidesPetsThatAreNotActive() {
        Pet anyPet = petRepository.findAll(PageRequest.of(0, 1)).getContent().get(0);

        // Same pet, wrong status -> treated as "does not exist" for public endpoints.
        assertTrue(petRepository.findByIdAndStatus(anyPet.getId(), PetStatus.HIDDEN).isEmpty());
        // Right status -> found.
        assertTrue(petRepository.findByIdAndStatus(anyPet.getId(), PetStatus.ACTIVE).isPresent());
    }

    @Test
    void findByIdWithRandomUuidIsEmpty() {
        // The Optional is empty -> in the service this becomes your 404.
        assertTrue(petRepository.findById(UUID.randomUUID()).isEmpty());
    }
}
