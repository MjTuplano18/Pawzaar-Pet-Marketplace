package com.pawzaar.pet.image;

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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JPA-slice test for {@link PetImageRepository} against the real PostgreSQL, with each test rolled
 * back. Covers the two derived queries the read path depends on (ordered images and the batch cover
 * lookup), the pet-scoped id lookup, and the foreign key's ON DELETE CASCADE.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PetImageRepositoryTest {

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private PetImageRepository petImageRepository;

    @Autowired
    private UserRepository userRepository;

    private Pet savedPet() {
        // pets.seller_id is a foreign key to users, so a seller must exist first.
        User seller = userRepository.save(User.register(
                "img-" + UUID.randomUUID() + "@pawzaar.test", "{bcrypt}irrelevant", "Image Tester"));
        return petRepository.save(Pet.create(
                seller.getId(), "Test pup", Species.DOG, "Aspin", 6,
                new BigDecimal("1000.00"), "friendly", "Manila", "Metro Manila", "MALE"));
    }

    private PetImage image(Pet pet, String key, int sortOrder) {
        return petImageRepository.save(PetImage.create(pet.getId(), key, "image/png", 128, sortOrder));
    }

    @Test
    void findsOnePetsImagesInSortOrder() {
        Pet pet = savedPet();
        image(pet, "second", 1);
        image(pet, "cover", 0);

        List<PetImage> images = petImageRepository.findByPetIdOrderBySortOrderAsc(pet.getId());

        assertEquals(2, images.size());
        assertEquals(0, images.get(0).getSortOrder());
        assertEquals(1, images.get(1).getSortOrder());
    }

    @Test
    void batchCoverLookupReturnsOnlySortOrderZeroForEachPet() {
        Pet a = savedPet();
        Pet b = savedPet();
        PetImage coverA = image(a, "a-cover", 0);
        image(a, "a-extra", 1);
        PetImage coverB = image(b, "b-cover", 0);

        List<PetImage> covers =
                petImageRepository.findByPetIdInAndSortOrder(List.of(a.getId(), b.getId()), 0);

        assertEquals(2, covers.size());
        assertTrue(covers.stream().map(PetImage::getId).toList()
                .containsAll(List.of(coverA.getId(), coverB.getId())));
    }

    @Test
    void findByIdAndPetIdScopesTheLookupToTheOwningPet() {
        Pet owner = savedPet();
        Pet other = savedPet();
        PetImage img = image(owner, "k", 0);

        assertTrue(petImageRepository.findByIdAndPetId(img.getId(), owner.getId()).isPresent());
        assertFalse(petImageRepository.findByIdAndPetId(img.getId(), other.getId()).isPresent());
    }

    @Test
    void deletingAPetCascadesToItsImages() {
        Pet pet = savedPet();
        image(pet, "k", 0);
        assertEquals(1, petImageRepository.countByPetId(pet.getId()));

        petRepository.deleteById(pet.getId());
        petRepository.flush();

        assertEquals(0, petImageRepository.countByPetId(pet.getId()));
    }
}
