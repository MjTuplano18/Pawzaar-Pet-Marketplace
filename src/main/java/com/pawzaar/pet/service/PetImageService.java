package com.pawzaar.pet.service;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.common.QuotaExceededException;
import com.pawzaar.common.limits.ListingLimitsProperties;
import com.pawzaar.pet.dto.PetImageResponse;
import com.pawzaar.common.image.ImageStorage;
import com.pawzaar.common.image.ImageValidator;
import com.pawzaar.pet.image.PetImage;
import com.pawzaar.pet.image.PetImageNotFoundException;
import com.pawzaar.pet.image.PetImageRepository;
import com.pawzaar.common.image.ServedImage;
import com.pawzaar.common.image.ValidatedImage;
import com.pawzaar.pet.repository.PetRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Upload, delete and serve the images attached to a listing.
 *
 * <p>Separate from {@link PetService} so the CRUD of a listing does not drag in storage concerns.
 * This service owns the two-sided write: a file on disk AND a row in the database. That pairing is
 * exactly what makes it worth its own class - it is the only place that has to keep the two in sync.
 *
 * <p>Authorization reuses {@link PetService#checkOwnership}: only the seller who owns the listing may
 * add or remove its images. The public read path additionally hides images of non-ACTIVE listings.
 */
@Service
public class PetImageService {

    private final PetRepository petRepository;
    private final PetImageRepository petImageRepository;
    private final ImageStorage petImageStorage;
    private final ImageValidator imageValidator;
    private final ListingLimitsProperties limits;

    public PetImageService(PetRepository petRepository,
                           PetImageRepository petImageRepository,
                           // @Qualifier picks WHICH of the three ImageStorage beans this service gets.
                           // Here: the bean named "petImageStorage" (the pet photos bucket). Without
                           // it, Spring would find 3 candidates of the same type and refuse to start.
                           @Qualifier("petImageStorage") ImageStorage petImageStorage,
                           ImageValidator imageValidator,
                           ListingLimitsProperties limits) {
        this.petRepository = petRepository;
        this.petImageRepository = petImageRepository;
        this.petImageStorage = petImageStorage;
        this.imageValidator = imageValidator;
        this.limits = limits;
    }

    /**
     * Validates the upload, stores the bytes, and records a row. The image is appended after the
     * existing ones, so the first upload becomes the cover (order 0).
     *
     * <p>H4: the new order is the CURRENT MAXIMUM plus one, not the row count. After a delete the
     * orders can be non-dense (e.g. 0 and 2), and using the count (2) would duplicate an existing
     * order. {@code (pet_id, sort_order)} is also UNIQUE in the database (V11), so a collision -
     * including two simultaneous uploads racing on the same max - is rejected rather than silently
     * storing an ambiguous ordering.
     *
     * @throws com.pawzaar.pet.PetNotFoundException      if the listing does not exist
     * @throws com.pawzaar.pet.ForbiddenPetAccessException if the caller is not the owner
     */
    @Transactional
    public PetImageResponse upload(UUID petId, UUID callerId, MultipartFile file) {
        Pet pet = requirePet(petId);
        PetService.checkOwnership(pet, callerId);

        // H6: cap the images per listing BEFORE spending work validating/storing the bytes.
        if (petImageRepository.countByPetId(petId) >= limits.getMaxImagesPerListing()) {
            throw new QuotaExceededException("images per listing", limits.getMaxImagesPerListing());
        }

        ValidatedImage validated = imageValidator.validate(file);
        String storageKey = petImageStorage.store(validated.data(), validated.extension());

        List<PetImage> existing = petImageRepository.findByPetIdOrderBySortOrderAsc(petId);
        int sortOrder = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1;
        try {
            PetImage saved = petImageRepository.save(PetImage.create(
                    petId, storageKey, validated.contentType(), validated.data().length, sortOrder));
            return new PetImageResponse(
                    saved.getId(),
                    PetService.imageUrl(petId, saved.getId()),
                    saved.getContentType(),
                    saved.getSortOrder());
        } catch (RuntimeException e) {
            // The database write failed: remove the file we just wrote so it does not become an
            // orphan the app can never reach.
            petImageStorage.delete(storageKey);
            throw e;
        }
    }

    /**
     * Deletes the row and then the file.
     *
     * <p>H4: after removing the row the survivors are renumbered to a dense 0..n-1 sequence, so the
     * cover (order 0) is restored when the cover itself was deleted and no gaps are left for a later
     * upload's max+1 to trip over.
     *
     * @throws PetNotFoundException       if the listing does not exist
     * @throws com.pawzaar.pet.ForbiddenPetAccessException if the caller is not the owner
     * @throws PetImageNotFoundException  if the image does not belong to this listing
     */
    @Transactional
    public void delete(UUID petId, UUID imageId, UUID callerId) {
        Pet pet = requirePet(petId);
        PetService.checkOwnership(pet, callerId);

        PetImage image = petImageRepository.findByIdAndPetId(imageId, petId)
                .orElseThrow(() -> new PetImageNotFoundException(imageId));

        petImageRepository.delete(image);

        // Reassign contiguous orders to the remaining images (the delete above is flushed first, so
        // this query sees only the survivors).
        int next = 0;
        for (PetImage survivor : petImageRepository.findByPetIdOrderBySortOrderAsc(petId)) {
            if (survivor.getSortOrder() != next) {
                survivor.reorder(next);
            }
            next++;
        }

        petImageStorage.delete(image.getStorageKey());
    }

    /**
     * Streams an image's bytes. Public callers may read images of ACTIVE listings only; the owner may
     * also see their own hidden/pending listing's images (they are still editing it). A non-owner
     * asking about a non-ACTIVE listing gets the same 404 as if the image did not exist - no leak.
     */
    @Transactional(readOnly = true)
    public ServedImage load(UUID petId, UUID imageId, UUID callerIdOrNull) {
        PetImage image = petImageRepository.findByIdAndPetId(imageId, petId)
                .orElseThrow(() -> new PetImageNotFoundException(imageId));

        Pet pet = requirePet(petId);
        boolean isOwner = callerIdOrNull != null && pet.getSellerId().equals(callerIdOrNull);
        if (pet.getStatus() != PetStatus.ACTIVE && !isOwner) {
            throw new PetNotFoundException(petId);
        }

        return new ServedImage(petImageStorage.load(image.getStorageKey()), image.getContentType());
    }

    private Pet requirePet(UUID petId) {
        return petRepository.findById(petId).orElseThrow(() -> new PetNotFoundException(petId));
    }
}
