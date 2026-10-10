package com.pawzaar.pet.service;

import com.pawzaar.common.PagedResponse;
import com.pawzaar.common.QuotaExceededException;
import com.pawzaar.common.limits.ListingLimitsProperties;
import com.pawzaar.pet.ForbiddenPetAccessException;
import com.pawzaar.pet.InvalidPetStatusException;
import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.dto.PetCreateRequest;
import com.pawzaar.pet.dto.PetFilter;
import com.pawzaar.pet.dto.PetImageResponse;
import com.pawzaar.pet.dto.PetResponse;
import com.pawzaar.pet.dto.PetSummary;
import com.pawzaar.pet.dto.PetUpdateRequest;
import com.pawzaar.pet.image.PetImage;
import com.pawzaar.pet.image.PetImageRepository;
import com.pawzaar.pet.repository.PetRepository;
import com.pawzaar.pet.repository.PetSpecifications;
import com.pawzaar.user.Role;
import com.pawzaar.user.User;
import com.pawzaar.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Business logic for the pet feature.
 *
 * <p>Ownership rule: any write operation on a listing checks that the caller's user ID
 * matches {@code pet.sellerId}. A mismatch throws {@link ForbiddenPetAccessException},
 * which the {@code GlobalExceptionHandler} renders as 403. The check lives HERE (not in
 * the controller) so it cannot be bypassed regardless of how the endpoint is reached.
 */
@Service
public class PetService {

    private final PetRepository petRepository;
    private final PetImageRepository petImageRepository;
    private final UserRepository userRepository;
    private final ListingLimitsProperties limits;

    public PetService(PetRepository petRepository,
                      PetImageRepository petImageRepository,
                      UserRepository userRepository,
                      ListingLimitsProperties limits) {
        this.petRepository = petRepository;
        this.petImageRepository = petImageRepository;
        this.userRepository = userRepository;
        this.limits = limits;
    }

    // ── READ ─────────────────────────────────────────────────────────────────────────────────

    /** Paginated list of ACTIVE listings matching the optional filters - the public search feed. */
    @Transactional(readOnly = true)
    public PagedResponse<PetSummary> listPets(PetFilter filter, Pageable pageable) {
        Page<Pet> page = petRepository.findAll(PetSpecifications.activeMatching(filter), pageable);
        Map<UUID, String> covers = coverUrlsFor(page.getContent());
        return PagedResponse.of(page.map(pet -> toSummary(pet, covers.get(pet.getId()))));
    }

    /** Full detail for one ACTIVE listing. Hidden and sold pets are invisible to the public. */
    @Transactional(readOnly = true)
    public PetResponse getPet(UUID id) {
        Pet pet = petRepository.findByIdAndStatus(id, PetStatus.ACTIVE)
                .orElseThrow(() -> new PetNotFoundException(id));
        return toResponse(pet, petImageRepository.findByPetIdOrderBySortOrderAsc(id));
    }

    /** All listings by the authenticated user (any status - it is their own dashboard). */
    @Transactional(readOnly = true)
    public PagedResponse<PetSummary> getMyPets(UUID sellerId, Pageable pageable) {
        Page<Pet> page = petRepository.findBySellerId(sellerId, pageable);
        Map<UUID, String> covers = coverUrlsFor(page.getContent());
        return PagedResponse.of(page.map(pet -> toSummary(pet, covers.get(pet.getId()))));
    }

    // ── WRITE ────────────────────────────────────────────────────────────────────────────────

    /** Creates a new listing owned by {@code sellerId}. */
    @Transactional
    public PetResponse createPet(UUID sellerId, PetCreateRequest request) {
        // H6: a per-account cap stops one seller from filling the marketplace. Soft-deleted
        // (HIDDEN) listings do not count, so deleting a listing frees the slot.
        long existing = petRepository.countBySellerIdAndStatusNot(sellerId, PetStatus.HIDDEN);
        if (existing >= limits.getMaxListingsPerUser()) {
            throw new QuotaExceededException("listings", limits.getMaxListingsPerUser());
        }

        Pet pet = Pet.create(
                sellerId,
                request.title(),
                request.species(),
                request.breed(),
                request.ageMonths(),
                request.price(),
                request.description(),
                request.city(),
                request.province(),
                request.sex()
        );
        // Moderation decision (H6): new listings go live immediately - status defaults to ACTIVE and
        // there is no review queue. PENDING_REVIEW exists for a future admin workflow but is not set
        // here. See the "Moderation" note in README.md.
        Pet saved = petRepository.save(pet);

        // A "seller" is simply a user who has posted a listing: posting your first pet promotes
        // the account from USER to SELLER. Idempotent, so later listings are no-ops. We do it here
        // (inside the same transaction as the insert) so the two states can never disagree.
        promoteToSeller(sellerId);

        return toResponse(saved, List.of());
    }

    /**
     * Full replacement of a listing's mutable fields.
     *
     * @param callerId the authenticated user's UUID (from the JWT {@code sub} claim)
     * @throws PetNotFoundException      if the id does not exist
     * @throws ForbiddenPetAccessException if the caller does not own the listing
     * @throws InvalidPetStatusException if the caller tries to set an admin-only status
     */
    @Transactional
    public PetResponse updatePet(UUID id, UUID callerId, PetUpdateRequest request) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new PetNotFoundException(id));

        checkOwnership(pet, callerId);

        // A seller controls only the PUBLIC lifecycle of their own listing (ACTIVE / SOLD / HIDDEN).
        // PENDING_REVIEW is reserved for an administrator flagging a listing, so reject it here -
        // this is the enforcement the DTO's comment always claimed. Rules with authorization
        // meaning belong in the service, where every caller path must pass through them.
        if (request.status() == PetStatus.PENDING_REVIEW) {
            throw new InvalidPetStatusException(request.status());
        }

        pet.setTitle(request.title());
        pet.setBreed(request.breed());
        pet.setAgeMonths(request.ageMonths());
        pet.setPrice(request.price());
        pet.setDescription(request.description());
        pet.setCity(request.city());
        pet.setProvince(request.province());
        pet.setSex(request.sex());
        pet.setStatus(request.status());

        // save() is not strictly needed (Hibernate dirty-checks within the transaction),
        // but being explicit makes it obvious that a write is intended.
        Pet updated = petRepository.save(pet);
        return toResponse(updated, petImageRepository.findByPetIdOrderBySortOrderAsc(id));
    }

    /**
     * Soft-deletes by setting status to HIDDEN. The row stays in the database so order
     * history and references remain intact. A future admin endpoint can hard-delete.
     *
     * @throws PetNotFoundException      if the id does not exist
     * @throws ForbiddenPetAccessException if the caller does not own the listing
     */
    @Transactional
    public void deletePet(UUID id, UUID callerId) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new PetNotFoundException(id));

        checkOwnership(pet, callerId);

        pet.setStatus(PetStatus.HIDDEN);
        petRepository.save(pet);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────────────────────

    /**
     * Ownership guard. Throws {@link ForbiddenPetAccessException} when the caller is not
     * the seller. This check must run AFTER confirming the pet exists; otherwise a 403
     * would leak the fact that the pet exists (which is its own information disclosure).
     */
    static void checkOwnership(Pet pet, UUID callerId) {
        if (!pet.getSellerId().equals(callerId)) {
            throw new ForbiddenPetAccessException(pet.getId());
        }
    }

    /**
     * Promotes a USER to SELLER the first time they create a listing. A missing user is ignored
     * (defensive): the caller already passed authentication, so this is a "should not happen",
     * and failing the whole request over a cosmetic role change would be worse than skipping it.
     */
    private void promoteToSeller(UUID sellerId) {
        userRepository.findById(sellerId).ifPresent(user -> {
            if (user.getRole() == Role.USER) {
                user.setRole(Role.SELLER);
                userRepository.save(user);
            }
        });
    }

    private static PetSummary toSummary(Pet pet, String coverImageUrl) {
        return new PetSummary(
                pet.getId(),
                pet.getTitle(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getPrice(),
                pet.getCity(),
                pet.getProvince(),
                pet.getSex(),
                pet.getStatus(),
                pet.getCreatedAt(),
                coverImageUrl
        );
    }

    private static PetResponse toResponse(Pet pet, List<PetImage> images) {
        return new PetResponse(
                pet.getId(),
                pet.getSellerId(),
                pet.getTitle(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getAgeMonths(),
                pet.getPrice(),
                pet.getDescription(),
                pet.getCity(),
                pet.getProvince(),
                pet.getSex(),
                pet.getStatus(),
                pet.getCreatedAt(),
                pet.getUpdatedAt(),
                images.stream().map(image -> toImageResponse(pet.getId(), image)).toList()
        );
    }

    private static PetImageResponse toImageResponse(UUID petId, PetImage image) {
        return new PetImageResponse(
                image.getId(),
                imageUrl(petId, image.getId()),
                image.getContentType(),
                image.getSortOrder()
        );
    }

    /** The public (relative) URL a client uses to fetch an image's bytes. */
    static String imageUrl(UUID petId, UUID imageId) {
        return "/api/v1/pets/" + petId + "/images/" + imageId;
    }

    /**
     * Batch-loads the cover (sort order 0) of every pet on the page in ONE query, then maps petId to
     * its URL. Doing it per pet would be the classic N+1: 21 queries for a 20-item page.
     */
    private Map<UUID, String> coverUrlsFor(List<Pet> pets) {
        List<UUID> petIds = pets.stream().map(Pet::getId).toList();
        if (petIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> urls = new HashMap<>();
        for (PetImage image : petImageRepository.findByPetIdInAndSortOrder(petIds, 0)) {
            urls.put(image.getPetId(), imageUrl(image.getPetId(), image.getId()));
        }
        return urls;
    }
}
