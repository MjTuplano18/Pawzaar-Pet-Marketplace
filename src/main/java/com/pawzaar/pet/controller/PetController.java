package com.pawzaar.pet.controller;

import com.pawzaar.common.PagedResponse;
import com.pawzaar.pet.dto.PetCreateRequest;
import com.pawzaar.pet.dto.PetResponse;
import com.pawzaar.pet.dto.PetSummary;
import com.pawzaar.pet.dto.PetUpdateRequest;
import com.pawzaar.pet.service.PetService;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;


/**
 * REST controller for the pet feature.
 *
 * <p>Public (no token required):
 * <ul>
 *   <li>GET  /api/v1/pets       - paginated list of ACTIVE listings</li>
 *   <li>GET  /api/v1/pets/{id}  - full details of one ACTIVE listing</li>
 * </ul>
 *
 * <p>Authenticated (Bearer token required):
 * <ul>
 *   <li>POST   /api/v1/pets          - create a listing (caller becomes the seller)</li>
 *   <li>PUT    /api/v1/pets/{id}     - replace a listing (owner only)</li>
 *   <li>DELETE /api/v1/pets/{id}     - soft-delete a listing (owner only)</li>
 *   <li>GET    /api/v1/me/pets       - list the caller's own listings (all statuses)</li>
 * </ul>
 *
 * <p>The controller is intentionally thin: parse, validate, delegate to the service,
 * return the DTO. Ownership checks and business rules live in {@link PetService}.
 */
@RestController
@RequestMapping("/api/v1")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    // ── PUBLIC ENDPOINTS ─────────────────────────────────────────────────────────────────────

    /**
     * Paginated list of ACTIVE listings.
     * Example: GET /api/v1/pets?page=0&size=20
     */
    @GetMapping("/pets")
    public PagedResponse<PetSummary> listPets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(size, 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return petService.listPets(pageable);
    }

    /**
     * Full details of one ACTIVE listing.
     * Example: GET /api/v1/pets/11111111-1111-1111-1111-111111111111
     */
    @GetMapping("/pets/{id}")
    public PetResponse getPet(@PathVariable UUID id) {
        return petService.getPet(id);
    }

    // ── AUTHENTICATED ENDPOINTS ──────────────────────────────────────────────────────────────

    /**
     * Creates a new listing. The caller (from the JWT) becomes the seller.
     * Returns 201 Created with a Location header pointing to the new resource.
     */
    @PostMapping("/pets")
    @PreAuthorize("hasAnyRole('USER', 'SELLER')")
    public ResponseEntity<PetResponse> createPet(
            @Valid @RequestBody PetCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sellerId = UUID.fromString(jwt.getSubject());
        PetResponse created = petService.createPet(sellerId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/pets/" + created.id()))
                .body(created);
    }

    /**
     * Full replacement of a listing's mutable fields.
     * Returns 200 with the updated listing. Returns 403 if the caller is not the owner,
     * 404 if the listing does not exist.
     */
    @PutMapping("/pets/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public PetResponse updatePet(
            @PathVariable UUID id,
            @Valid @RequestBody PetUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID callerId = UUID.fromString(jwt.getSubject());
        return petService.updatePet(id, callerId, request);
    }

    /**
     * Soft-deletes a listing (sets status to HIDDEN).
     * Returns 204 No Content on success, 403 if not the owner, 404 if not found.
     */
    @DeleteMapping("/pets/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> deletePet(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        UUID callerId = UUID.fromString(jwt.getSubject());
        petService.deletePet(id, callerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns all of the authenticated user's listings (any status).
     * Example: GET /api/v1/me/pets?page=0&size=20
     */
    @GetMapping("/me/pets")
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<PetSummary> getMyPets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sellerId = UUID.fromString(jwt.getSubject());
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(size, 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return petService.getMyPets(sellerId, pageable);
    }
}
