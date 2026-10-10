package com.pawzaar.pet.service;

import com.pawzaar.common.image.ImageStorage;
import com.pawzaar.common.image.ImageStorageException;
import com.pawzaar.pet.Pet;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.image.PetImage;
import com.pawzaar.pet.image.PetImageRepository;
import com.pawzaar.pet.repository.PetRepository;
import com.pawzaar.user.User;
import com.pawzaar.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * H5: deleting a stored image is deferred until the surrounding transaction COMMITS.
 *
 * <p>A unit test with mocks cannot prove this - it needs a real transaction and a real (local)
 * storage backend, so a rolled-back delete can be shown to leave the file untouched. These are
 * integration tests: they boot the context against the Docker PostgreSQL and write under a test root.
 *
 * <p>There is no test-managed transaction here (the commit path must actually commit), so each test
 * cleans up the rows and files it created; otherwise its fixtures would leak into the count-based
 * {@code PetRepositoryTest} (they share the same database).
 */
@SpringBootTest(properties = {
        "pawzaar.storage.type=local",
        "pawzaar.storage.root=target/h5-test-uploads"
})
class PetImageDeletionAfterCommitTest {

    @Autowired
    private PetImageService petImageService;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetImageRepository petImageRepository;

    @Autowired
    @Qualifier("petImageStorage")
    private ImageStorage storage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> petIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        for (UUID petId : petIds) {
            for (PetImage image : petImageRepository.findByPetIdOrderBySortOrderAsc(petId)) {
                petImageRepository.delete(image);
                deleteFileQuietly(image.getStorageKey());
            }
            petRepository.deleteById(petId);
        }
        userIds.forEach(userRepository::deleteById);
        petIds.clear();
        userIds.clear();
    }

    /** Returns the id of a freshly stored file that is actually present on disk. */
    private String storedKey() {
        String key = storage.store("bytes".getBytes(StandardCharsets.UTF_8), "png");
        assertDoesNotThrow(() -> storage.load(key), "precondition: the file exists");
        return key;
    }

    private PetImage imageForNewListing(String key) {
        User seller = userRepository.save(User.register(
                "h5-" + UUID.randomUUID() + "@pawzaar.test", "{bcrypt}dev-only-hash", "H5 Seller"));
        Pet pet = petRepository.save(Pet.create(
                seller.getId(), "H5 Listing", Species.DOG, "Aspin", 12,
                new BigDecimal("1500.00"), null, "Cebu City", "Cebu", "MALE"));
        PetImage image = petImageRepository.save(
                PetImage.create(pet.getId(), key, "image/png", 5, 0));

        petIds.add(pet.getId());
        userIds.add(seller.getId());
        return image;
    }

    private void deleteFileQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
            // the file may already be gone (the committed-delete test) - that is fine
        }
    }

    @Test
    void aCommittedDeleteRemovesTheStoredFile() {
        PetImage image = imageForNewListing(storedKey());

        petImageService.delete(image.getPetId(), image.getId(), ownerOf(image));

        assertThrows(ImageStorageException.class, () -> storage.load(image.getStorageKey()),
                "after a committed delete the file must be gone");
    }

    @Test
    void aRolledBackDeleteKeepsTheStoredFile() {
        PetImage image = imageForNewListing(storedKey());

        // run the delete inside a transaction we then force to roll back
        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    petImageService.delete(image.getPetId(), image.getId(), ownerOf(image));
                    throw new IllegalStateException("force rollback");
                }));

        // The row survives the rollback, so its file MUST survive too - otherwise the API would
        // serve a broken image.
        assertDoesNotThrow(() -> storage.load(image.getStorageKey()),
                "a rolled-back delete must not touch the file");
    }

    private UUID ownerOf(PetImage image) {
        return petRepository.findById(image.getPetId()).orElseThrow().getSellerId();
    }
}
