package com.pawzaar.pet.service;

import com.pawzaar.common.PagedResponse;
import com.pawzaar.pet.ForbiddenPetAccessException;
import com.pawzaar.pet.InvalidPetStatusException;
import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.dto.PetCreateRequest;
import com.pawzaar.pet.dto.PetFilter;
import com.pawzaar.pet.dto.PetResponse;
import com.pawzaar.pet.dto.PetSummary;
import com.pawzaar.pet.dto.PetUpdateRequest;
import com.pawzaar.pet.repository.PetRepository;
import com.pawzaar.user.Role;
import com.pawzaar.user.User;
import com.pawzaar.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PetService - no Spring context, no database.
 *
 * <p>Key rules exercised:
 * <ul>
 *   <li>listPets always filters on ACTIVE status</li>
 *   <li>getPet returns 404 for non-ACTIVE or missing pets</li>
 *   <li>createPet stores the correct sellerId</li>
 *   <li>updatePet enforces ownership (user A cannot update user B's listing)</li>
 *   <li>updatePet rejects the admin-only PENDING_REVIEW status</li>
 *   <li>deletePet soft-deletes (HIDDEN) and enforces ownership</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PetService petService;

    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PET_ID   = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // ── listPets ───────────────────────────────────────────────────────────────

    @Test
    void listPetsQueriesRepositoryWithActiveFilter() {
        PageRequest pageable = PageRequest.of(0, 20);
        PetFilter filter = new PetFilter(Species.DOG, null, null, null, null, null, null, null);
        when(petRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(activePet(OWNER_ID))));

        PagedResponse<PetSummary> result = petService.listPets(filter, pageable);

        assertEquals(1, result.content().size());
        verify(petRepository).findAll(any(Specification.class), eq(pageable));
    }

    // ── getPet ─────────────────────────────────────────────────────────────────

    @Test
    void getPetThrowsNotFoundWhenPetMissingOrNotActive() {
        UUID missingId = UUID.randomUUID();
        when(petRepository.findByIdAndStatus(missingId, PetStatus.ACTIVE))
                .thenReturn(Optional.empty());

        PetNotFoundException ex = assertThrows(
                PetNotFoundException.class, () -> petService.getPet(missingId));
        assertEquals(missingId, ex.getPetId());
    }

    // ── createPet ──────────────────────────────────────────────────────────────

    @Test
    void createPetPersistsAndReturnsResponse() {
        Pet saved = petWithId(OWNER_ID, PET_ID);
        when(petRepository.save(any(Pet.class))).thenReturn(saved);

        PetCreateRequest req = new PetCreateRequest(
                "Fluffy Shih Tzu", Species.DOG, "Shih Tzu", 12,
                new BigDecimal("9000.00"), "House trained.", "Quezon City", "Metro Manila", "FEMALE");

        PetResponse response = petService.createPet(OWNER_ID, req);

        assertEquals(PET_ID, response.id());
        assertEquals(OWNER_ID, response.sellerId());
        verify(petRepository).save(any(Pet.class));
    }

    @Test
    void createPetPromotesAUserToSellerOnTheirFirstListing() {
        when(petRepository.save(any(Pet.class))).thenReturn(petWithId(OWNER_ID, PET_ID));

        // A freshly registered account is a plain USER.
        User user = User.register("ana@pawzaar.test", "{bcrypt}irrelevant", "Ana Reyes");
        assertEquals(Role.USER, user.getRole());
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user));

        PetCreateRequest req = new PetCreateRequest(
                "Fluffy Shih Tzu", Species.DOG, "Shih Tzu", 12,
                new BigDecimal("9000.00"), "House trained.", "Quezon City", "Metro Manila", "FEMALE");

        petService.createPet(OWNER_ID, req);

        // Posting a listing is what makes someone a seller.
        assertEquals(Role.SELLER, user.getRole());
        verify(userRepository).save(user);
    }

    // ── updatePet ──────────────────────────────────────────────────────────────

    @Test
    void updatePetByOwnerSucceeds() {
        Pet existing = petWithId(OWNER_ID, PET_ID);
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(existing));
        when(petRepository.save(existing)).thenReturn(existing);

        PetUpdateRequest req = new PetUpdateRequest(
                "Updated title", "Shih Tzu", 13,
                new BigDecimal("9500.00"), "Updated desc.",
                "Quezon City", "Metro Manila", "FEMALE", PetStatus.ACTIVE);

        PetResponse response = petService.updatePet(PET_ID, OWNER_ID, req);

        assertEquals(PET_ID, response.id());
        verify(petRepository).save(existing);
    }

    @Test
    void updatePetByNonOwnerThrowsForbidden() {
        // The pet belongs to OWNER_ID; OTHER_ID tries to update it.
        Pet existing = petWithId(OWNER_ID, PET_ID);
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(existing));

        PetUpdateRequest req = new PetUpdateRequest(
                "Hacked title", null, 1,
                BigDecimal.ONE, null, "Somewhere", "Nowhere", null, PetStatus.ACTIVE);

        ForbiddenPetAccessException ex = assertThrows(
                ForbiddenPetAccessException.class,
                () -> petService.updatePet(PET_ID, OTHER_ID, req));

        assertEquals(PET_ID, ex.getPetId());
        // Ownership check must fire BEFORE any save.
        verify(petRepository, never()).save(any());
    }

    @Test
    void updatePetThrowsNotFoundWhenMissing() {
        when(petRepository.findById(PET_ID)).thenReturn(Optional.empty());

        PetUpdateRequest req = new PetUpdateRequest(
                "title", null, 1, BigDecimal.ONE, null, "city", "province", null, PetStatus.ACTIVE);

        assertThrows(PetNotFoundException.class,
                () -> petService.updatePet(PET_ID, OWNER_ID, req));
    }

    @Test
    void updatePetRejectsAdminOnlyStatus() {
        Pet existing = petWithId(OWNER_ID, PET_ID);
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(existing));

        PetUpdateRequest req = new PetUpdateRequest(
                "title", null, 1, BigDecimal.ONE, null,
                "city", "province", null, PetStatus.PENDING_REVIEW);

        InvalidPetStatusException ex = assertThrows(
                InvalidPetStatusException.class,
                () -> petService.updatePet(PET_ID, OWNER_ID, req));

        assertEquals(PetStatus.PENDING_REVIEW, ex.getStatus());
        // The guard runs before any mutation/save: the pet stays ACTIVE.
        assertEquals(PetStatus.ACTIVE, existing.getStatus());
        verify(petRepository, never()).save(any());
    }

    // ── deletePet ──────────────────────────────────────────────────────────────

    @Test
    void deletePetByOwnerSoftDeletesListing() {
        Pet existing = petWithId(OWNER_ID, PET_ID);
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(existing));
        when(petRepository.save(existing)).thenReturn(existing);

        petService.deletePet(PET_ID, OWNER_ID);

        // Soft-delete: status changed to HIDDEN, not a physical DELETE.
        assertEquals(PetStatus.HIDDEN, existing.getStatus());
        verify(petRepository).save(existing);
        verify(petRepository, never()).deleteById(any());
    }

    @Test
    void deletePetByNonOwnerThrowsForbidden() {
        Pet existing = petWithId(OWNER_ID, PET_ID);
        when(petRepository.findById(PET_ID)).thenReturn(Optional.of(existing));

        assertThrows(ForbiddenPetAccessException.class,
                () -> petService.deletePet(PET_ID, OTHER_ID));

        verify(petRepository, never()).save(any());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Creates a Pet via reflection (protected constructor) with a known sellerId and id. */
    private static Pet petWithId(UUID sellerId, UUID petId) {
        Pet pet = newPet();
        setField(pet, "id", petId);
        setField(pet, "sellerId", sellerId);
        pet.setTitle("Test Pet");
        pet.setSpecies(Species.DOG);
        pet.setCity("Manila");
        pet.setProvince("Metro Manila");
        pet.setPrice(new BigDecimal("10000.00"));
        pet.setStatus(PetStatus.ACTIVE);
        return pet;
    }

    private static Pet activePet(UUID sellerId) {
        return petWithId(sellerId, UUID.randomUUID());
    }

    private static Pet newPet() {
        try {
            var ctor = Pet.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
