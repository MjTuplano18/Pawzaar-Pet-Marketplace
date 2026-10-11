package com.pawzaar.adoption;

import com.pawzaar.adoption.dto.AdoptionRequestResponse;
import com.pawzaar.adoption.dto.CreateAdoptionRequestRequest;
import com.pawzaar.adoption.dto.UpdateAdoptionStatusRequest;
import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.repository.PetRepository;
import com.pawzaar.user.User;
import com.pawzaar.user.UserNotFoundException;
import com.pawzaar.user.repository.UserRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for adoption requests.
 *
 * <p>Access rules enforced here (not in the controller):
 * <ul>
 *   <li>Only the requester may WITHDRAW their own request.</li>
 *   <li>Only the pet owner may APPROVE or REJECT a request.</li>
 *   <li>A pet must be ACTIVE to accept new requests.</li>
 *   <li>A requester may not own the pet they are requesting.</li>
 *   <li>A requester may not have an existing PENDING request for the same pet.</li>
 * </ul>
 */
@Service
public class AdoptionService {

    private final AdoptionRequestRepository adoptionRequestRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;

    public AdoptionService(AdoptionRequestRepository adoptionRequestRepository,
                           PetRepository petRepository,
                           UserRepository userRepository) {
        this.adoptionRequestRepository = adoptionRequestRepository;
        this.petRepository = petRepository;
        this.userRepository = userRepository;
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new adoption request for the given pet, submitted by {@code requesterId}.
     *
     * @throws PetNotFoundException          if the pet does not exist or is not ACTIVE
     * @throws AccessDeniedException         if the requester owns the pet
     * @throws DuplicateAdoptionRequestException if a PENDING request already exists
     */
    @Transactional
    public AdoptionRequestResponse createRequest(UUID petId, UUID requesterId,
                                                  CreateAdoptionRequestRequest body) {
        // Pet must exist and be ACTIVE
        Pet pet = petRepository.findByIdAndStatus(petId, PetStatus.ACTIVE)
                .orElseThrow(() -> new PetNotFoundException(petId));

        // Owners cannot adopt their own pets
        if (pet.getSellerId().equals(requesterId)) {
            throw new AccessDeniedException("You cannot request to adopt your own listing");
        }

        // No duplicate PENDING requests
        if (adoptionRequestRepository.existsByPetIdAndRequesterIdAndStatus(
                petId, requesterId, AdoptionStatus.PENDING)) {
            throw new DuplicateAdoptionRequestException();
        }

        AdoptionRequest saved = adoptionRequestRepository.save(
                AdoptionRequest.create(petId, requesterId, body == null ? null : body.message()));

        return toDto(saved, pet, requesterOf(requesterId));
    }

    /**
     * All requests for a single pet — visible only to the pet's owner.
     *
     * @throws PetNotFoundException    if the pet does not exist
     * @throws AccessDeniedException   if the caller is not the pet's seller
     */
    @Transactional(readOnly = true)
    public List<AdoptionRequestResponse> getRequestsForPet(UUID petId, UUID callerId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new PetNotFoundException(petId));

        if (!pet.getSellerId().equals(callerId)) {
            throw new AccessDeniedException("Only the pet owner can view its adoption requests");
        }

        return adoptionRequestRepository.findByPetId(petId).stream()
                .map(r -> toDto(r, pet, requesterOf(r.getRequesterId())))
                .toList();
    }

    /** All requests sent by the authenticated user (their own adoption history). */
    @Transactional(readOnly = true)
    public List<AdoptionRequestResponse> getMyRequests(UUID requesterId) {
        return adoptionRequestRepository.findByRequesterId(requesterId).stream()
                .map(r -> {
                    Pet pet = petRepository.findById(r.getPetId()).orElse(null);
                    User requester = requesterOf(r.getRequesterId());
                    String petTitle = pet != null ? pet.getTitle() : "(deleted)";
                    return new AdoptionRequestResponse(
                            r.getId(), r.getPetId(), petTitle,
                            r.getRequesterId(), requester != null ? requester.getDisplayName() : "Unknown",
                            r.getMessage(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
                })
                .toList();
    }

    /**
     * All requests received across every pet the seller owns.
     * Expensive for sellers with many listings; acceptable for MVP (no pagination on this view).
     */
    @Transactional(readOnly = true)
    public List<AdoptionRequestResponse> getReceivedRequests(UUID sellerId) {
        return petRepository.findBySellerId(sellerId, Pageable.unpaged())
                .stream()
                .flatMap(pet -> adoptionRequestRepository.findByPetId(pet.getId()).stream()
                        .map(r -> toDto(r, pet, requesterOf(r.getRequesterId()))))
                .toList();
    }

    /**
     * Updates the status of a single request.
     *
     * <ul>
     *   <li>APPROVE / REJECT → only the pet owner.</li>
     *   <li>WITHDRAW         → only the requester.</li>
     * </ul>
     *
     * @throws AdoptionRequestNotFoundException if the request does not exist
     * @throws AccessDeniedException            if the caller is not authorised for this transition
     */
    @Transactional
    public AdoptionRequestResponse updateStatus(UUID requestId, UUID callerId,
                                                 UpdateAdoptionStatusRequest body) {
        AdoptionRequest request = adoptionRequestRepository.findById(requestId)
                .orElseThrow(() -> new AdoptionRequestNotFoundException(requestId));

        Pet pet = petRepository.findById(request.getPetId())
                .orElseThrow(() -> new PetNotFoundException(request.getPetId()));

        AdoptionStatus newStatus = body.status();

        if (newStatus == AdoptionStatus.WITHDRAWN) {
            // Only the requester can withdraw
            if (!request.getRequesterId().equals(callerId)) {
                throw new AccessDeniedException("Only the requester can withdraw their request");
            }
        } else if (newStatus == AdoptionStatus.APPROVED || newStatus == AdoptionStatus.REJECTED) {
            // Only the pet owner can approve or reject
            if (!pet.getSellerId().equals(callerId)) {
                throw new AccessDeniedException("Only the pet owner can approve or reject a request");
            }
        } else {
            throw new AccessDeniedException("Invalid status transition");
        }

        request.setStatus(newStatus);
        AdoptionRequest updated = adoptionRequestRepository.save(request);
        return toDto(updated, pet, requesterOf(updated.getRequesterId()));
    }

    /**
     * Returns a single request. Access is allowed to the requester and the pet owner only.
     *
     * @throws AdoptionRequestNotFoundException if the request does not exist
     * @throws AccessDeniedException            if the caller is unrelated to the request
     */
    @Transactional(readOnly = true)
    public AdoptionRequestResponse getRequest(UUID requestId, UUID callerId) {
        AdoptionRequest request = adoptionRequestRepository.findById(requestId)
                .orElseThrow(() -> new AdoptionRequestNotFoundException(requestId));

        Pet pet = petRepository.findById(request.getPetId())
                .orElseThrow(() -> new PetNotFoundException(request.getPetId()));

        boolean isRequester = request.getRequesterId().equals(callerId);
        boolean isOwner = pet.getSellerId().equals(callerId);

        if (!isRequester && !isOwner) {
            throw new AccessDeniedException("You do not have access to this adoption request");
        }

        return toDto(request, pet, requesterOf(request.getRequesterId()));
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────────────────────

    private AdoptionRequestResponse toDto(AdoptionRequest r, Pet pet, User requester) {
        return new AdoptionRequestResponse(
                r.getId(),
                r.getPetId(),
                pet != null ? pet.getTitle() : "(deleted)",
                r.getRequesterId(),
                requester != null ? requester.getDisplayName() : "Unknown",
                r.getMessage(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private User requesterOf(UUID requesterId) {
        return userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException(requesterId));
    }
}
