package com.pawzaar.adoption;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.repository.PetRepository;
import com.pawzaar.user.User;
import com.pawzaar.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository slice test for the adoption_requests foreign keys (V14/V15).
 *
 * <p>Boots JPA against the real PostgreSQL in Docker; each test builds its own fixtures
 * inside the transaction, which is rolled back afterwards (same pattern as
 * {@code PetRepositoryTest}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdoptionRequestRepositoryTest {

    @Autowired
    private AdoptionRequestRepository adoptionRequestRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void deletingAPetCascadesItsAdoptionRequests() {
        // V15: a permanent listing delete must not be blocked by (or orphan) adoption
        // requests. pet_images already cascades (V7); this pins the adoption_requests FK.
        User seller = userRepository.saveAndFlush(User.register(
                "seller-" + UUID.randomUUID() + "@pawzaar.test",
                "{bcrypt}dev-only-hash", "Fixture Seller"));
        User adopter = userRepository.saveAndFlush(User.register(
                "adopter-" + UUID.randomUUID() + "@pawzaar.test",
                "{bcrypt}dev-only-hash", "Fixture Adopter"));

        Pet pet = petRepository.saveAndFlush(Pet.create(seller.getId(), "Cascade pup",
                Species.DOG, null, 3, BigDecimal.valueOf(5000), null, "Cebu City", "Cebu", null));

        adoptionRequestRepository.saveAndFlush(
                AdoptionRequest.create(pet.getId(), adopter.getId(), "I have a question"));

        assertTrue(adoptionRequestRepository.findAll().stream()
                .anyMatch(r -> r.getPetId().equals(pet.getId())));

        petRepository.delete(pet);
        petRepository.flush();

        assertFalse(adoptionRequestRepository.findAll().stream()
                .anyMatch(r -> r.getPetId().equals(pet.getId())),
                "adoption requests must be removed together with their pet (ON DELETE CASCADE)");
    }
}
