package com.pawzaar.pet.repository;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.dto.PetFilter;
import com.pawzaar.user.User;
import com.pawzaar.user.repository.UserRepository;
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
 * Repository slice test: boots JPA + the real PostgreSQL in Docker.
 *
 * <p>C1: these tests no longer depend on the old V3 seed rows (the seed now lives in a
 * dev-only Flyway location that tests never apply). Every test creates its own fixtures
 * inside the transaction, which is rolled back afterwards - so tests are isolated from
 * each other AND from any leftover data in the shared database.
 *
 * <p>{@code replace = NONE} is required because there is no in-memory H2 on the classpath;
 * instead we point at the Docker database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PetRepositoryTest {

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private UserRepository userRepository;

    // ── fixtures: every test builds its own seller + pets ─────────────────────────

    /** A brand-new seller row; the email is unique per call so parallel runs never collide. */
    private User seller() {
        return userRepository.saveAndFlush(User.register(
                "seller-" + UUID.randomUUID() + "@pawzaar.test",
                "{bcrypt}dev-only-hash", "Fixture Seller"));
    }

    private Pet pet(User seller, String title, Species species, String breed,
                    int ageMonths, int price, String city, String province) {
        Pet pet = Pet.create(seller.getId(), title, species, breed, ageMonths,
                BigDecimal.valueOf(price), null, city, province, null);
        return petRepository.saveAndFlush(pet);
    }

    // ── plain repository queries ─────────────────────────────────────────────────

    @Test
    void paginationCountsAllPets() {
        User seller = seller();
        pet(seller, "A", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");
        pet(seller, "B", Species.DOG, "Labrador", 4, 11000, "City B", "Province A");
        pet(seller, "C", Species.DOG, "Labrador", 5, 12000, "City C", "Province A");

        Page<Pet> page = petRepository.findAll(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void findByIdWithSavedIdReturnsPet() {
        Pet saved = pet(seller(), "Fixture pup", Species.DOG, "Aspin", 5, 2500, "Cebu City", "Cebu");

        var found = petRepository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("Fixture pup", found.get().getTitle());
        assertNotNull(found.get().getSellerId());
    }

    @Test
    void findByStatusReturnsOnlyActivePets() {
        User seller = seller();
        pet(seller, "A", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");
        pet(seller, "B", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");
        pet(seller, "C", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");

        Page<Pet> active = petRepository.findByStatus(PetStatus.ACTIVE, PageRequest.of(0, 10));

        assertEquals(3, active.getTotalElements());
        assertTrue(active.getContent().stream().allMatch(p -> p.getStatus() == PetStatus.ACTIVE));
    }

    @Test
    void findByIdAndStatusHidesPetsThatAreNotActive() {
        Pet saved = pet(seller(), "Fixture pup", Species.DOG, "Aspin", 5, 2500, "Cebu City", "Cebu");

        // Same pet, wrong status -> treated as "does not exist" for public endpoints.
        assertTrue(petRepository.findByIdAndStatus(saved.getId(), PetStatus.HIDDEN).isEmpty());
        // Right status -> found.
        assertTrue(petRepository.findByIdAndStatus(saved.getId(), PetStatus.ACTIVE).isPresent());
    }

    @Test
    void findByIdWithRandomUuidIsEmpty() {
        // The Optional is empty -> in the service this becomes your 404.
        assertTrue(petRepository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findBySellerIdReturnsAllListingsForThatSeller() {
        User seller = seller();
        pet(seller, "A", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");
        pet(seller, "B", Species.DOG, "Labrador", 4, 11000, "City B", "Province A");
        pet(seller, "C", Species.DOG, "Labrador", 5, 12000, "City C", "Province A");

        Page<Pet> myPets = petRepository.findBySellerId(seller.getId(), PageRequest.of(0, 10));

        assertEquals(3, myPets.getTotalElements());
        assertTrue(myPets.getContent().stream()
                .allMatch(p -> seller.getId().equals(p.getSellerId())));
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
        User seller = seller();
        pet(seller, "A", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");
        pet(seller, "B", Species.DOG, "Labrador", 4, 11000, "City B", "Province A");
        pet(seller, "C", Species.DOG, "Labrador", 5, 12000, "City C", "Province A");

        assertEquals(3, search(noFilters()).getTotalElements());
    }

    @Test
    void specFiltersBySpecies() {
        User seller = seller();
        pet(seller, "A", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");
        pet(seller, "B", Species.DOG, "Labrador", 4, 11000, "City B", "Province A");
        pet(seller, "C", Species.CAT, "Persian", 2, 12000, "City C", "Province A");

        Page<Pet> dogs = search(new PetFilter(
                Species.DOG, null, null, null, null, null, null, null));

        assertEquals(2, dogs.getTotalElements());
        assertTrue(dogs.getContent().stream().allMatch(p -> p.getSpecies() == Species.DOG));
    }

    @Test
    void specFiltersByProvinceAndByCity() {
        User seller = seller();
        pet(seller, "Friendly Golden Retriever puppy", Species.DOG, "Golden Retriever",
                3, 15000, "Meycauayan", "Bulacan");
        pet(seller, "Shih Tzu, 1 year old", Species.DOG, "Shih Tzu",
                12, 9000, "Quezon City", "Metro Manila");

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
        User seller = seller();
        pet(seller, "A", Species.DOG, "Golden Retriever", 3, 15000, "Meycauayan", "Bulacan");

        Page<Pet> retriever = search(new PetFilter(
                null, null, null, "retriever", null, null, null, null));

        assertEquals(1, retriever.getTotalElements());
        assertEquals("Golden Retriever", retriever.getContent().get(0).getBreed());
    }

    @Test
    void specCityAndProvinceMatchCaseInsensitively() {
        // M3: the stored values are Title Case; a client may send any casing.
        User seller = seller();
        pet(seller, "GR", Species.DOG, "Golden Retriever", 3, 15000, "Quezon City", "Metro Manila");
        pet(seller, "Shih", Species.DOG, "Shih Tzu", 12, 9000, "Cebu City", "Cebu");

        Page<Pet> byProvince = search(new PetFilter(
                null, "metro manila", null, null, null, null, null, null));
        assertEquals(1, byProvince.getTotalElements());
        assertEquals("Golden Retriever", byProvince.getContent().get(0).getBreed());

        Page<Pet> byCity = search(new PetFilter(
                null, null, "CEBU CITY", null, null, null, null, null));
        assertEquals(1, byCity.getTotalElements());
        assertEquals("Shih Tzu", byCity.getContent().get(0).getBreed());
    }

    @Test
    void specBreedSearchTreatsWildcardsLiterally() {
        // M3: '%' must be matched as a character, not as "match anything". Unescaped, the term
        // "50%" would become LIKE '%50%%' and match BOTH breeds (they both contain "50").
        User seller = seller();
        pet(seller, "A", Species.DOG, "Golden 50% Retriever", 3, 15000, "City A", "Province A");
        pet(seller, "B", Species.DOG, "Golden 50 Retriever", 4, 12000, "City B", "Province A");

        Page<Pet> literal = search(new PetFilter(
                null, null, null, "50%", null, null, null, null));

        assertEquals(1, literal.getTotalElements());
        assertEquals("Golden 50% Retriever", literal.getContent().get(0).getBreed());
    }

    @Test
    void specBreedSearchTreatsUnderscoreLiterally() {
        // M3: '_' is the single-character wildcard; escaped, it must not match "pugxa".
        User seller = seller();
        pet(seller, "A", Species.DOG, "pug_a", 3, 15000, "City A", "Province A");
        pet(seller, "B", Species.DOG, "pugxa", 4, 12000, "City B", "Province A");

        Page<Pet> literal = search(new PetFilter(
                null, null, null, "pug_a", null, null, null, null));

        assertEquals(1, literal.getTotalElements());
        assertEquals("pug_a", literal.getContent().get(0).getBreed());
    }

    @Test
    void specSortsByTheIdTiebreakerWhenValuesTie() {
        // M3: equal sort values are ordered deterministically by id, so paging cannot drop or
        // duplicate a row. Ids are random UUIDs, so we sort them in Java to know the expectation.
        User seller = seller();
        Pet a = pet(seller, "A", Species.DOG, "Labrador", 3, 10000, "City A", "Province A");
        Pet b = pet(seller, "B", Species.DOG, "Labrador", 3, 10000, "City B", "Province A");
        Pet c = pet(seller, "C", Species.DOG, "Labrador", 3, 10000, "City C", "Province A");

        Page<Pet> page = petRepository.findAll(
                PetSpecifications.activeMatching(noFilters()),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "price")
                        .and(Sort.by(Sort.Direction.ASC, "id"))));

        List<UUID> returned = page.getContent().stream().map(Pet::getId).toList();
        // PostgreSQL compares UUIDs as unsigned 128-bit values; the canonical string form has the
        // same ordering, whereas UUID.compareTo is signed and would disagree on high-bit ids.
        List<UUID> expected = java.util.stream.Stream.of(a.getId(), b.getId(), c.getId())
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList();
        assertEquals(expected, returned);
    }

    @Test
    void specFiltersByPriceRange() {
        // Fixture prices: 15000 (Golden Retriever), 9000 (Shih Tzu), 12000 (Persian).
        User seller = seller();
        pet(seller, "GR", Species.DOG, "Golden Retriever", 3, 15000, "Meycauayan", "Bulacan");
        pet(seller, "Shih", Species.DOG, "Shih Tzu", 12, 9000, "Quezon City", "Metro Manila");
        pet(seller, "Persian", Species.CAT, "Persian", 2, 12000, "Angeles", "Pampanga");

        Page<Pet> midRange = search(new PetFilter(
                null, null, null, null, new BigDecimal("10000"), new BigDecimal("14000"), null, null));

        assertEquals(1, midRange.getTotalElements());
        assertEquals("Persian", midRange.getContent().get(0).getBreed());
    }

    @Test
    void specFiltersByAgeRange() {
        // Fixture ages: 3 (GR), 12 (Shih Tzu), 2 (Persian). [2,3] matches two.
        User seller = seller();
        pet(seller, "GR", Species.DOG, "Golden Retriever", 3, 15000, "Meycauayan", "Bulacan");
        pet(seller, "Shih", Species.DOG, "Shih Tzu", 12, 9000, "Quezon City", "Metro Manila");
        pet(seller, "Persian", Species.CAT, "Persian", 2, 12000, "Angeles", "Pampanga");

        Page<Pet> young = search(new PetFilter(
                null, null, null, null, null, null, 2, 3));

        assertEquals(2, young.getTotalElements());
    }

    @Test
    void specCombinesFiltersWithAnd() {
        // DOG AND Metro Manila -> only the Shih Tzu (the Golden Retriever lives in Bulacan).
        User seller = seller();
        pet(seller, "Friendly Golden Retriever puppy", Species.DOG, "Golden Retriever",
                3, 15000, "Meycauayan", "Bulacan");
        pet(seller, "Shih Tzu, 1 year old", Species.DOG, "Shih Tzu",
                12, 9000, "Quezon City", "Metro Manila");
        pet(seller, "Persian kitten", Species.CAT, "Persian", 2, 12000, "Angeles", "Pampanga");

        Page<Pet> result = search(new PetFilter(
                Species.DOG, "Metro Manila", null, null, null, null, null, null));

        assertEquals(1, result.getTotalElements());
        assertEquals("Shih Tzu, 1 year old", result.getContent().get(0).getTitle());
    }

    @Test
    void specNeverReturnsNonActivePets() {
        User seller = seller();
        Pet persian = pet(seller, "Persian kitten", Species.CAT, "Persian",
                2, 12000, "Angeles", "Pampanga");

        persian.setStatus(PetStatus.HIDDEN);
        petRepository.saveAndFlush(persian);

        // A CAT filter would normally match it, but the spec always pins status = ACTIVE.
        Page<Pet> cats = search(new PetFilter(
                Species.CAT, null, null, null, null, null, null, null));

        assertEquals(0, cats.getTotalElements());
    }

    @Test
    void specResultsHonourThePageableSort() {
        User seller = seller();
        pet(seller, "GR", Species.DOG, "Golden Retriever", 3, 15000, "Meycauayan", "Bulacan");
        pet(seller, "Persian", Species.CAT, "Persian", 2, 12000, "Angeles", "Pampanga");
        pet(seller, "Shih", Species.DOG, "Shih Tzu", 12, 9000, "Quezon City", "Metro Manila");

        Page<Pet> byPriceAsc = petRepository.findAll(
                PetSpecifications.activeMatching(noFilters()),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "price")));

        List<BigDecimal> prices = byPriceAsc.getContent().stream()
                .map(p -> p.getPrice().setScale(2))   // normalize scale: pg may return 9000 vs 9000.00
                .toList();
        assertEquals(
                List.of(new BigDecimal("9000.00"), new BigDecimal("12000.00"), new BigDecimal("15000.00")),
                prices);
    }
}