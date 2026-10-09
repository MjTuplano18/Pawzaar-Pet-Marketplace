package com.pawzaar.pet.service;

import com.pawzaar.pet.ForbiddenPetAccessException;
import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.dto.PetImageResponse;
import com.pawzaar.pet.image.ImageStorage;
import com.pawzaar.pet.image.ImageValidator;
import com.pawzaar.pet.image.PetImage;
import com.pawzaar.pet.image.PetImageNotFoundException;
import com.pawzaar.pet.image.PetImageRepository;
import com.pawzaar.pet.image.ServedImage;
import com.pawzaar.pet.image.ValidatedImage;
import com.pawzaar.pet.repository.PetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PetImageService} - no Spring, no disk, no database.
 *
 * <p>The interesting behaviour is the file/row pairing and the authorization around it:
 * storing only for the owner, cleaning up the file if the row write fails, and never revealing
 * a non-ACTIVE listing's image to a stranger.
 */
@ExtendWith(MockitoExtension.class)
class PetImageServiceTest {

    @Mock private PetRepository petRepository;
    @Mock private PetImageRepository petImageRepository;
    @Mock private ImageStorage imageStorage;
    @Mock private ImageValidator imageValidator;

    @InjectMocks private PetImageService petImageService;

    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PET_ID   = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IMAGE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // ── upload ─────────────────────────────────────────────────────────────────

    @Test
    void uploadValidatesStoresAndPersists() {
        Pet pet = petOwnedBy(OWNER_ID, PetStatus.ACTIVE);
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(pet));
        when(imageValidator.validate(any())).thenReturn(new ValidatedImage(new byte[]{1, 2, 3}, "png", "image/png"));
        when(imageStorage.store(any(), eq("png"))).thenReturn("storage-key");
        when(petImageRepository.countByPetId(PET_ID)).thenReturn(0L);
        when(petImageRepository.save(any(PetImage.class))).thenReturn(imageWithId(PET_ID, IMAGE_ID, "storage-key", 0));

        PetImageResponse response = petImageService.upload(PET_ID, OWNER_ID, anyFile());

        assertEquals(IMAGE_ID, response.id());
        assertEquals("/api/v1/pets/" + PET_ID + "/images/" + IMAGE_ID, response.url());
        assertEquals("image/png", response.contentType());
        assertEquals(0, response.sortOrder());
        verify(imageStorage).store(any(), eq("png"));
        verify(petImageRepository).save(any(PetImage.class));
    }

    @Test
    void uploadByNonOwnerIsForbiddenAndTouchesNothing() {
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(petOwnedBy(OWNER_ID, PetStatus.ACTIVE)));

        assertThrows(ForbiddenPetAccessException.class,
                () -> petImageService.upload(PET_ID, OTHER_ID, anyFile()));

        // Rejected before validating or writing anything.
        verifyNoInteractions(imageValidator, imageStorage);
        verify(petImageRepository, never()).save(any());
    }

    @Test
    void uploadRemovesTheFileIfTheDatabaseWriteFails() {
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(petOwnedBy(OWNER_ID, PetStatus.ACTIVE)));
        when(imageValidator.validate(any())).thenReturn(new ValidatedImage(new byte[]{1, 2, 3}, "png", "image/png"));
        when(imageStorage.store(any(), eq("png"))).thenReturn("storage-key");
        when(petImageRepository.countByPetId(PET_ID)).thenReturn(0L);
        when(petImageRepository.save(any(PetImage.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class,
                () -> petImageService.upload(PET_ID, OWNER_ID, anyFile()));

        // No orphan file is left behind when the row cannot be written.
        verify(imageStorage).delete("storage-key");
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    void deleteRemovesTheRowAndTheFile() {
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(petOwnedBy(OWNER_ID, PetStatus.ACTIVE)));
        PetImage image = imageWithId(PET_ID, IMAGE_ID, "storage-key", 0);
        when(petImageRepository.findByIdAndPetId(IMAGE_ID, PET_ID)).thenReturn(Optional.of(image));

        petImageService.delete(PET_ID, IMAGE_ID, OWNER_ID);

        verify(petImageRepository).delete(image);
        verify(imageStorage).delete("storage-key");
    }

    @Test
    void deleteOfAMissingImageIsNotFound() {
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(petOwnedBy(OWNER_ID, PetStatus.ACTIVE)));
        when(petImageRepository.findByIdAndPetId(IMAGE_ID, PET_ID)).thenReturn(Optional.empty());

        assertThrows(PetImageNotFoundException.class,
                () -> petImageService.delete(PET_ID, IMAGE_ID, OWNER_ID));

        verify(petImageRepository, never()).delete(any());
        verifyNoInteractions(imageStorage);
    }

    // ── load ───────────────────────────────────────────────────────────────────

    @Test
    void loadReturnsTheBytesForAnActiveListing() {
        PetImage image = imageWithId(PET_ID, IMAGE_ID, "storage-key", 0);
        when(petImageRepository.findByIdAndPetId(IMAGE_ID, PET_ID)).thenReturn(Optional.of(image));
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(petOwnedBy(OWNER_ID, PetStatus.ACTIVE)));
        when(imageStorage.load("storage-key")).thenReturn(new ByteArrayResource(new byte[]{9, 9}));

        ServedImage served = petImageService.load(PET_ID, IMAGE_ID, null);

        assertEquals("image/png", served.contentType());
        verify(imageStorage).load("storage-key");
    }

    @Test
    void loadHidesANonActiveListingsImageFromAStranger() {
        PetImage image = imageWithId(PET_ID, IMAGE_ID, "storage-key", 0);
        when(petImageRepository.findByIdAndPetId(IMAGE_ID, PET_ID)).thenReturn(Optional.of(image));
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(petOwnedBy(OWNER_ID, PetStatus.HIDDEN)));

        // A stranger (or an anonymous caller) must not learn that a hidden listing has images.
        assertThrows(PetNotFoundException.class,
                () -> petImageService.load(PET_ID, IMAGE_ID, OTHER_ID));
        verifyNoInteractions(imageStorage);
    }

    @Test
    void loadLetsTheOwnerSeeTheirOwnHiddenListingsImage() {
        PetImage image = imageWithId(PET_ID, IMAGE_ID, "storage-key", 0);
        when(petImageRepository.findByIdAndPetId(IMAGE_ID, PET_ID)).thenReturn(Optional.of(image));
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(petOwnedBy(OWNER_ID, PetStatus.HIDDEN)));
        when(imageStorage.load("storage-key")).thenReturn(new ByteArrayResource(new byte[]{1}));

        ServedImage served = petImageService.load(PET_ID, IMAGE_ID, OWNER_ID);

        assertEquals("image/png", served.contentType());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static MockMultipartFile anyFile() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
    }

    private static Pet petOwnedBy(UUID sellerId, PetStatus status) {
        Pet pet = Pet.create(sellerId, "Test pup", Species.DOG, "Aspin", 6,
                new BigDecimal("1000.00"), "friendly", "Manila", "Metro Manila", "MALE");
        pet.setStatus(status);
        return pet;
    }

    private static PetImage imageWithId(UUID petId, UUID imageId, String storageKey, int sortOrder) {
        PetImage image = PetImage.create(petId, storageKey, "image/png", 100, sortOrder);
        setField(image, "id", imageId);
        return image;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
