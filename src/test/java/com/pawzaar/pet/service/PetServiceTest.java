package com.pawzaar.pet.service;

import com.pawzaar.pet.Pet;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.dto.PetSummary;
import com.pawzaar.pet.repository.PetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UNIT test of the service: no Spring context, no database, no HTTP.
 * The repository is a mock, so this runs in milliseconds.
 *
 * <p>What it protects: the BUSINESS RULES. In particular the status filter - if someone
 * reverted listPets() to findAll(...), the repository tests would still pass and the bug
 * would ship. Only a test that inspects what the service asked for can catch it.
 */
@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock               // a fake PetRepository: returns whatever we tell it to
    private PetRepository petRepository;

    @InjectMocks        // builds PetService for us, passing the mock into the constructor
    private PetService petService;

    @Test
    void listPetsOnlyAsksTheRepositoryForActivePets() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(petRepository.findByStatus(eq(PetStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activePet())));

        Page<PetSummary> result = petService.listPets(pageable);

        assertEquals(1, result.getContent().size());
        // The important assertion: the STATUS FILTER really reaches the database.
        verify(petRepository).findByStatus(PetStatus.ACTIVE, pageable);
        // ...and the old unfiltered call is never used again.
        verify(petRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getPetThrowsNotFoundWhenThePetIsMissingOrNotActive() {
        UUID missingId = UUID.randomUUID();
        when(petRepository.findByIdAndStatus(missingId, PetStatus.ACTIVE))
                .thenReturn(Optional.empty());   // same empty result for "gone" and "not active"

        PetNotFoundException ex = assertThrows(
                PetNotFoundException.class, () -> petService.getPet(missingId));

        assertEquals(missingId, ex.getPetId());
    }

    private static Pet activePet() {
        // Built through reflection-free helpers: the entity has a protected constructor,
        // so tests create it with setters only for the fields the test cares about.
        Pet pet = newPet();
        pet.setTitle("Friendly Golden Retriever puppy");
        pet.setSpecies(com.pawzaar.pet.Species.DOG);
        pet.setCity("Meycauayan");
        pet.setProvince("Bulacan");
        pet.setPrice(new java.math.BigDecimal("15000.00"));
        pet.setStatus(PetStatus.ACTIVE);
        return pet;
    }

    private static Pet newPet() {
        try {
            // Pet's constructor is protected; a test in another package cannot call new Pet()
            // directly. Going through the declared constructor keeps the entity rules intact.
            var ctor = Pet.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
