package com.pawzaar.pet.repository;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.dto.PetFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void findBySellerIdReturnsAllListingsForThatSeller() {
        // All 3 seed pets belong to seller 11111111-1111-1111-1111-111111111111.
        UUID seedSellerId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Page<Pet> myPets = petRepository.findBySellerId(
                seedSellerId, PageRequest.of(0, 10));

        assertEquals(3, myPets.getTotalElements());
        assertTrue(myPets.getContent().stream()
                .allMatch(p -> seedSellerId.equals(p.getSellerId())));
    }

    @Test
    void findBySellerIdReturnsEmptyForUnknownSeller() {
        Page<Pet> myPets = petRepository.findBySellerId(
                UUID.randomUUID(), PageRequest.of(0, 10));

        assertEquals(0, myPets.getTotalElements());
    }

    // ── Step 10: dynamic search filters, exercised against the REAL database ─────────────
    //
    // The controller/service tests mock the repository, so only these tests prove the
    // generated SQL (the JPA Specifications) actually does what we claim.

    /** A filter with every field null = "no filters". */
    private static PetFilter noFilters() {
        return new PetFilter(null, null, null, null, null, null, null, null);
    }

    private Page<Pet> search(PetFilter filter) {
        return petRepository.findAll(
                PetSpecifications.activeMatching(filter),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void specWithNoFiltersReturnsEveryActivePet() {
        assertEquals(3, search(noFilters()).getTotalElements());
    }

    @Test
    void specFiltersBySpecies() {
        Page<Pet> dogs = search(new PetFilter(
                Species.DOG, null, null, null, null, null, null, null));

        assertEquals(2, dogs.getTotalElements());
        assertTrue(dogs.getContent().stream().allMatch(p -> p.getSpecies() == Species.DOG));
    }

    @Test
    void specFiltersByProvinceAndByCity() {
        // Province filter.
        Page<Pet> inBulacan = search(new PetFilter(
                null, "Bulacan", null, null, null, null, null, null));
        assertEquals(1, inBulacan.getTotalElements());
        assertEquals("Friendly Golden Retriever puppy", inBulacan.getContent().get(0).getTitle());

        // City-only filter.
        Page<Pet> inQuezonCity = search(new PetFilter(
                null, null, "Quezon City", null, null, null, null, null));
        assertEquals(1, inQuezonCity.getTotalElements());
        assertEquals("Shih Tzu, 1 year old", inQuezonCity.getContent().get(0).getTitle());
    }

    @Test
    void specBreedSearchIsCaseInsensitiveContains() {
        // Stored as "Golden Retriever"; a lowercase partial search must still match.
        Page<Pet> retriever = search(new PetFilter(
                null, null, null, "retriever", null, null, null, null));

        assertEquals(1, retriever.getTotalElements());
        assertEquals("Golden Retriever", retriever.getContent().get(0).getBreed());
    }

    @Test
    void specFiltersByPriceRange() {
        // Seed prices: 15000 (Golden Retriever), 9000 (Shih Tzu), 12000 (Persian).
        Page<Pet> midRange = search(new PetFilter(
                null, null, null, null, new BigDecimal("10000"), new BigDecimal("14000"), null, null));

        assertEquals(1, midRange.getTotalElements());
        assertEquals("Persian", midRange.getContent().get(0).getBreed());
    }

    @Test
    void specFiltersByAgeRange() {
        // Seed ages: 3 (Golden Retriever), 12 (Shih Tzu), 2 (Persian). [2,3] matches two.
        Page<Pet> young = search(new PetFilter(
                null, null, null, null, null, null, 2, 3));

        assertEquals(2, young.getTotalElements());
    }

    @Test
    void specCombinesFiltersWithAnd() {
        // DOG AND Metro Manila -> only the Shih Tzu (the Golden Retriever lives in Bulacan).
        Page<Pet> result = search(new PetFilter(
                Species.DOG, "Metro Manila", null, null, null, null, null, null));

        assertEquals(1, result.getTotalElements());
        assertEquals("Shih Tzu, 1 year old", result.getContent().get(0).getTitle());
    }

    @Test
    void specNeverReturnsNonActivePets() {
        Pet persian = petRepository.findAll().stream()
                .filter(p -> "Persian".equals(p.getBreed()))
                .findFirst()
                .orElseThrow();

        persian.setStatus(PetStatus.HIDDEN);
        petRepository.saveAndFlush(persian);

        // A CAT filter would normally match it, but the spec always pins status = ACTIVE.
        Page<Pet> cats = search(new PetFilter(
                Species.CAT, null, null, null, null, null, null, null));

        assertEquals(0, cats.getTotalElements());
    }

    @Test
    void specResultsHonourThePageableSort() {
        Page<Pet> byPriceAsc = petRepository.findAll(
                PetSpecifications.activeMatching(noFilters()),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "price")));

        List<BigDecimal> prices = byPriceAsc.getContent().stream().map(Pet::getPrice).toList();
        assertEquals(
                List.of(new BigDecimal("9000.00"), new BigDecimal("12000.00"), new BigDecimal("15000.00")),
                prices);
    }
}
