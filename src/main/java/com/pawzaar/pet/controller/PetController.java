package com.pawzaar.pet.controller;

import com.pawzaar.pet.dto.PetResponse; // DTO returned when viewing one pet's details.
import com.pawzaar.pet.dto.PetSummary; // DTO returned for pet listing results.
import com.pawzaar.pet.service.PetService; // Handles the business logic for pet operations.

import org.springframework.data.domain.Page; // Represents a paginated result.
import org.springframework.data.domain.PageRequest; // Creates pagination and sorting settings.
import org.springframework.data.domain.Sort; // Defines the order of the results.

import org.springframework.web.bind.annotation.GetMapping; // Maps methods to HTTP GET requests.
import org.springframework.web.bind.annotation.PathVariable; // Reads values from the URL path.
import org.springframework.web.bind.annotation.RequestMapping; // Defines the base URL for this controller.
import org.springframework.web.bind.annotation.RequestParam; // Reads values from query parameters.
import org.springframework.web.bind.annotation.RestController; // Marks this class as a REST controller.

import java.util.UUID; // Used for the pet's unique identifier.


/**
 * REST controller for the pet feature.
 * Handles HTTP requests related to pet listings and delegates
 * the actual business logic to PetService.
 * Endpoints:
 * GET /api/v1/pets       - Returns a paginated list of pets.
 * GET /api/v1/pets/{id} - Returns the details of one pet.
 * Controller → Service → Repository → Database
 */
@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    // Service responsible for retrieving and processing pet data.
    private final PetService petService;


    // Spring injects PetService through the constructor.
    public PetController(PetService petService) {
        this.petService = petService;
    }


    /**
     * Returns a paginated list of pets.
     * Example:
     * GET /api/v1/pets?page=0&size=20
     * Results are sorted by creation date, with the newest
     * pet listings appearing first.
     */
    @GetMapping
    public Page<PetSummary> listPets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Prevents clients from requesting an excessively large page.
        int safeSize = Math.min(size, 50);

        // Prevents invalid negative page numbers.
        int safePage = Math.max(page, 0);

        // Creates the pagination and sorting configuration.
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        // Delegates the database operation to the service layer.
        return petService.listPets(pageable);
    }


    /**
     * Returns the details of a single pet.
     * Example:
     * GET /api/v1/pets/11111111-1111-1111-1111-111111111111
     * An invalid UUID format results in a 400 response.
     * A valid UUID that does not exist is handled as a 404.
     */
    @GetMapping("/{id}")
    public PetResponse getPet(@PathVariable UUID id) {

        // Delegates the request to the service layer.
        return petService.getPet(id);
    }
}