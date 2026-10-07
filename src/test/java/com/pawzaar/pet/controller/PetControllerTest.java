package com.pawzaar.pet.controller;

import com.pawzaar.config.SecurityConfig;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.dto.PetResponse;
import com.pawzaar.pet.dto.PetSummary;
import com.pawzaar.pet.service.PetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test: boots ONLY the web layer (controller + advice + security)
 * - no database, no real service. The service is replaced by a Mockito mock.
 */
@WebMvcTest(PetController.class)
@Import(SecurityConfig.class)   // pull OUR security rules into the slice, so the whitelist is tested too
class PetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Replaces the real PetService bean: this slice has no database,
    // so the real one could not run. We pre-program its answers per test.
    @MockitoBean
    private PetService petService;

    private static final UUID PET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void listIsPublicAndReturnsPageJson() throws Exception {
        PetSummary pet = new PetSummary(
                PET_ID, "Friendly Golden Retriever puppy", Species.DOG, "Golden Retriever",
                new BigDecimal("15000.00"), "Meycauayan", "Bulacan", PetStatus.ACTIVE,
                Instant.parse("2026-10-07T06:00:00Z"));

        // When the service is asked for any page, hand back a page with one pet.
        when(petService.listPets(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pet)));

        // No login, no headers -> but GET /api/v1/pets is public:
        mockMvc.perform(get("/api/v1/pets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Friendly Golden Retriever puppy"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detailReturnsFullPetJson() throws Exception {
        PetResponse pet = new PetResponse(
                PET_ID, UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Persian kitten", Species.CAT, "Persian", 2,
                new BigDecimal("12000.00"), "Litter trained, with papers.",
                "Angeles", "Pampanga", PetStatus.ACTIVE, Instant.parse("2026-10-07T06:00:00Z"));

        when(petService.getPet(PET_ID)).thenReturn(pet);

        mockMvc.perform(get("/api/v1/pets/{id}", PET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Litter trained, with papers."))
                .andExpect(jsonPath("$.ageMonths").value(2));
    }

    @Test
    void missingPetYields404ProblemDetail() throws Exception {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(petService.getPet(missing)).thenThrow(new PetNotFoundException(missing));

        mockMvc.perform(get("/api/v1/pets/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Pet not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownRouteIsBlocked() throws Exception {
        // Not whitelisted -> the filter chain rejects it BEFORE the controller.
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }
}
