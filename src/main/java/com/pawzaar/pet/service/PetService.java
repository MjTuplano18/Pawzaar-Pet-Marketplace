package com.pawzaar.pet.service;

import com.pawzaar.pet.Pet; // Represents the pet entity stored in the database.
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.dto.PetResponse; // DTO used when returning one pet's full details.
import com.pawzaar.pet.dto.PetSummary; // DTO used when returning pet listing summaries.
import com.pawzaar.pet.repository.PetRepository; // Handles database operations for pets.

import org.springframework.data.domain.Page; // Represents a paginated collection of results.
import org.springframework.data.domain.Pageable; // Contains pagination and sorting information.
import org.springframework.stereotype.Service; // Marks this class as a Spring service.
import org.springframework.transaction.annotation.Transactional; // Controls database transaction behavior.

import java.util.UUID; // Represents the unique ID of a pet.


/**
 * Handles the business logic for the pet feature.
 * The service sits between the controller and repository:
 * Controller → Service → Repository → Database
 * It retrieves pet data, applies business rules, and converts
 * database entities into DTOs before returning them to the controller.
 */
@Service
public class PetService {

    // Repository used to access pet data from the database.
    private final PetRepository petRepository;

    // Spring automatically injects PetRepository through this constructor.
    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    /**
     * Retrieves a paginated list of pets.
     * The result is converted from Pet entities into PetSummary DTOs
     * before being returned to the controller.
     * readOnly = true: Hibernate skips change-tracking - a performance hint,
     * because this method can never modify data.
     */
    @Transactional(readOnly = true)
    public Page<PetSummary> listPets(Pageable pageable) {
        return petRepository.findByStatus(PetStatus.ACTIVE, pageable)
                .map(PetService::toSummary);
    }

    // The transaction closes when this method returns; the Pet entity becomes
    // detached, and only the DTO leaves this layer.
    @Transactional(readOnly = true)
    public PetResponse getPet(UUID id) {
        Pet pet = petRepository.findByIdAndStatus(id,PetStatus.ACTIVE)
                .orElseThrow(() -> new PetNotFoundException(id));   // empty Optional -> exception
        return toResponse(pet);
    }

    /**
     * Converts a Pet entity into a PetSummary DTO.
     * Used for the pet listing endpoint where only basic information is needed.
     */
    private static PetSummary toSummary(Pet pet) {
        return new PetSummary(
                pet.getId(),
                pet.getTitle(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getPrice(),
                pet.getCity(),
                pet.getProvince(),
                pet.getStatus(),
                pet.getCreatedAt()
        );
    }

    /**
     * Converts a Pet entity into a PetResponse DTO.
     * Used for the pet detail endpoint where the complete
     * pet information is required.
     */
    private static PetResponse toResponse(Pet pet) {
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
                pet.getStatus(),
                pet.getCreatedAt()
        );
    }
}