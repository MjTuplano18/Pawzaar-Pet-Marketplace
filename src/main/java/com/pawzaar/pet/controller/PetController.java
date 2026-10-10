package com.pawzaar.pet.controller;

import com.pawzaar.common.PagedResponse;
import com.pawzaar.pet.InvalidSortException;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.dto.PetCreateRequest;
import com.pawzaar.pet.dto.PetFilter;
import com.pawzaar.pet.dto.PetImageResponse;
import com.pawzaar.pet.dto.PetResponse;
import com.pawzaar.pet.dto.PetSummary;
import com.pawzaar.pet.dto.PetUpdateRequest;
import com.pawzaar.common.image.ServedImage;
import com.pawzaar.pet.service.PetImageService;
import com.pawzaar.pet.service.PetService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Set;
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
 * <p>Listing images (owner only to write, public to read):
 * <ul>
 *   <li>POST   /api/v1/pets/{id}/images             - upload an image (multipart)</li>
 *   <li>DELETE /api/v1/pets/{id}/images/{imageId}   - remove an image</li>
 *   <li>GET    /api/v1/pets/{id}/images/{imageId}   - fetch the image bytes</li>
 * </ul>
 *
 * <p>The controller is intentionally thin: parse, validate, delegate to the service,
 * return the DTO. Ownership checks and business rules live in {@link PetService}.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class PetController {

    /**
     * Entity properties a client may sort the public feed by. Client input is NEVER passed
     * straight to Spring Data: an unknown property throws PropertyReferenceException (a 500),
     * and sorting by an unindexed column is a cheap denial-of-service. Allowlist, then validate.
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "price", "ageMonths");

    private final PetService petService;
    private final PetImageService petImageService;

    public PetController(PetService petService, PetImageService petImageService) {
        this.petService = petService;
        this.petImageService = petImageService;
    }

    // ── PUBLIC ENDPOINTS ─────────────────────────────────────────────────────────────────────

    /**
     * Paginated list of ACTIVE listings, optionally filtered and sorted.
     * Example: GET /api/v1/pets?page=0&size=20&species=DOG&maxPrice=10000&sort=price&order=asc
     */
    @GetMapping("/pets")
    public PagedResponse<PetSummary> listPets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1") int size,
            @RequestParam(required = false) Species species,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minAgeMonths,
            @RequestParam(required = false) Integer maxAgeMonths,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order) {

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(size, 50),
                parseSort(sort, order));

        PetFilter filter = new PetFilter(
                species, province, city, breed,
                minPrice, maxPrice, minAgeMonths, maxAgeMonths);

        return petService.listPets(filter, pageable);
    }

    /**
     * Turns the client's sort key + order into a Spring Data Sort, refusing anything not on
     * the allowlist. This is the only place sort input is read, and it is never trusted.
     *
     * <p>M3: {@code id} is always appended as a tiebreaker. Without it, rows with equal
     * {@code price}/{@code ageMonths}/timestamps have no defined order, so a row can appear on two
     * pages (or none) as the client pages through. {@code id} is unique, which makes the order total.
     */
    private static Sort parseSort(String sort, String order) {
        if (!SORTABLE_FIELDS.contains(sort)) {
            throw new InvalidSortException("sort", sort, SORTABLE_FIELDS);
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(order);   // asc/desc, case-insensitive
        } catch (IllegalArgumentException e) {
            throw new InvalidSortException("order", order, List.of("asc", "desc"));
        }
        return Sort.by(direction, sort).and(Sort.by(Sort.Direction.ASC, "id"));
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
     *
     * <p>H1: any AUTHENTICATED user may reach this endpoint - ownership is enforced by
     * {@code PetService.checkOwnership}. Requiring {@code hasRole('SELLER')} here would 403 a
     * brand-new seller, whose token still says USER until it expires, on their own listing
     * (the role is promoted in the DB at create time, but the JWT is stale for up to 30 min).
     */
    @PutMapping("/pets/{id}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER')")
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
     * (Same H1 reasoning as updatePet: the owner is gated by ownership, not a role claim.)
     */
    @DeleteMapping("/pets/{id}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER')")
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
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1") int size,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sellerId = UUID.fromString(jwt.getSubject());
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(size, 50),
                // M3: id tiebreaker keeps "my listings" stable across pages when created_at ties.
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

        return petService.getMyPets(sellerId, pageable);
    }

    // ── LISTING IMAGES ───────────────────────────────────────────────────────────────────────

    /**
     * Uploads one image for a listing (multipart/form-data, field name {@code file}).
     * Returns 201 with a Location header pointing at the new image's bytes.
     *
     * <p>Allowed for USER or SELLER; the service then enforces that the caller owns the listing.
     */
    @PostMapping(path = "/pets/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'SELLER')")
    public ResponseEntity<PetImageResponse> uploadPetImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        UUID callerId = UUID.fromString(jwt.getSubject());
        PetImageResponse created = petImageService.upload(id, callerId, file);
        return ResponseEntity.created(URI.create(created.url())).body(created);
    }

    /** Removes an image from a listing (owner only). Returns 204. */
    @DeleteMapping("/pets/{id}/images/{imageId}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER')")
    public ResponseEntity<Void> deletePetImage(
            @PathVariable UUID id,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID callerId = UUID.fromString(jwt.getSubject());
        petImageService.delete(id, imageId, callerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Streams an image's bytes. Public for ACTIVE listings; the owner may also fetch images of their
     * own non-ACTIVE listing. Sends a long cache header because image bytes are immutable.
     */
    @GetMapping("/pets/{id}/images/{imageId}")
    public ResponseEntity<Resource> getPetImage(
            @PathVariable UUID id,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID callerId = jwt == null ? null : UUID.fromString(jwt.getSubject());
        ServedImage image = petImageService.load(id, imageId, callerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(image.resource());
    }
}
