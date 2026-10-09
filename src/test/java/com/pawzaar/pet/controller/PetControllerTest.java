package com.pawzaar.pet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawzaar.config.JwtConfig;
import com.pawzaar.config.SecurityConfig;
import com.pawzaar.pet.ForbiddenPetAccessException;
import com.pawzaar.pet.InvalidPetStatusException;
import com.pawzaar.pet.PetNotFoundException;
import com.pawzaar.pet.PetStatus;
import com.pawzaar.pet.Species;
import com.pawzaar.pet.dto.PetCreateRequest;
import com.pawzaar.pet.dto.PetFilter;
import com.pawzaar.pet.dto.PetResponse;
import com.pawzaar.pet.dto.PetSummary;
import com.pawzaar.pet.dto.PetUpdateRequest;
import com.pawzaar.pet.service.PetService;
import com.pawzaar.common.PagedResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer slice tests for PetController.
 *
 * <p>Covers:
 * <ul>
 *   <li>Public GET endpoints are accessible without a token</li>
 *   <li>POST/PUT/DELETE require authentication (401 when anonymous)</li>
 *   <li>Role-based access (@PreAuthorize): USER may create, but only SELLER may update/delete</li>
 *   <li>Ownership check: a different user's token gets 403</li>
 *   <li>Input validation: missing required fields get 400</li>
 *   <li>Framework errors keep the ProblemDetail shape (bad UUID -> 400, malformed JSON -> 400)</li>
 *   <li>Not-found: missing pet id gets 404 ProblemDetail</li>
 * </ul>
 */
@WebMvcTest(PetController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class PetControllerTest {

    @Autowired MockMvc mockMvc;

    // @WebMvcTest slices don't register ObjectMapper as a bean in this Spring Boot version.
    // Instantiate directly - same configuration, no wiring needed.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules(); // registers JavaTimeModule for Instant serialization

    @MockitoBean PetService petService;

    private static final UUID OWNER_ID  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID  = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PET_ID    = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // ── helpers ────────────────────────────────────────────────────────────────

    /** JWT post-processor for a given user id (defaults to the SELLER role). */
    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId) {
        return jwtFor(userId, "SELLER");
    }

    /**
     * JWT post-processor for a user id AND role.
     *
     * We set the AUTHORITIES explicitly on purpose: MockMvc's jwt() post-processor builds the
     * Authentication by hand and does NOT run our JwtAuthenticationConverter, so the "role" claim
     * alone earns no authority. In production the converter does that mapping; here we mirror it.
     */
    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt()
                .jwt(b -> b.subject(userId.toString()).claim("role", role))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private PetSummary fakeSummary() {
        return new PetSummary(PET_ID, "Golden Retriever pup", Species.DOG, "Golden Retriever",
                new BigDecimal("15000.00"), "Meycauayan", "Bulacan", "MALE",
                PetStatus.ACTIVE, Instant.parse("2026-10-07T06:00:00Z"), null);
    }

    private PetResponse fakeResponse() {
        return new PetResponse(PET_ID, OWNER_ID, "Golden Retriever pup", Species.DOG,
                "Golden Retriever", 3, new BigDecimal("15000.00"),
                "Vaccinated, dewormed, playful.", "Meycauayan", "Bulacan", "MALE",
                PetStatus.ACTIVE, Instant.parse("2026-10-07T06:00:00Z"),
                Instant.parse("2026-10-07T06:00:00Z"), List.of());
    }

    // ── public read endpoints ──────────────────────────────────────────────────

    @Test
    void listIsPublicAndReturnsPagedResponse() throws Exception {
        when(petService.listPets(any(), any()))
                .thenReturn(new PagedResponse<>(List.of(fakeSummary()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/pets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Golden Retriever pup"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void listPassesQueryParametersToTheService() throws Exception {
        when(petService.listPets(any(), any()))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/pets")
                        .param("species", "DOG")
                        .param("province", "Bulacan")
                        .param("maxPrice", "10000"))
                .andExpect(status().isOk());

        // The controller must map the query string into a PetFilter and hand it to the service.
        ArgumentCaptor<PetFilter> captor = ArgumentCaptor.forClass(PetFilter.class);
        verify(petService).listPets(captor.capture(), any());
        assertEquals(Species.DOG, captor.getValue().species());
        assertEquals("Bulacan", captor.getValue().province());
        assertEquals(new BigDecimal("10000"), captor.getValue().maxPrice());
    }

    @Test
    void listMapsSortAndOrderToAPageable() throws Exception {
        when(petService.listPets(any(), any()))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/pets")
                        .param("sort", "price")
                        .param("order", "asc"))
                .andExpect(status().isOk());

        // The controller must translate sort/order into a Sort on the Pageable it passes down.
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(petService).listPets(any(), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("price");
        assertEquals("price", order.getProperty());
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void unknownSortFieldYields400ProblemDetail() throws Exception {
        // "password" is not on the allowlist - must be rejected, never handed to Spring Data.
        mockMvc.perform(get("/api/v1/pets").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid sort parameter"));
    }

    @Test
    void invalidSortOrderYields400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/pets").param("order", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid sort parameter"));
    }

    @Test
    void listClampsPagingToSafeBounds() throws Exception {
        when(petService.listPets(any(), any()))
                .thenReturn(new PagedResponse<>(List.of(), 0, 50, 0, 0));

        mockMvc.perform(get("/api/v1/pets").param("page", "-3").param("size", "999"))
                .andExpect(status().isOk());

        // Negative page -> 0; oversized page size -> capped at 50.
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(petService).listPets(any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    void listDefaultsToNewestFirst() throws Exception {
        when(petService.listPets(any(), any()))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/pets"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(petService).listPets(any(), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("createdAt");
        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void detailIsPublicAndReturnsFullPetJson() throws Exception {
        when(petService.getPet(PET_ID)).thenReturn(fakeResponse());

        mockMvc.perform(get("/api/v1/pets/{id}", PET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sex").value("MALE"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void missingPetYields404ProblemDetail() throws Exception {
        UUID missing = UUID.randomUUID();
        when(petService.getPet(missing)).thenThrow(new PetNotFoundException(missing));

        mockMvc.perform(get("/api/v1/pets/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Pet not found"));
    }

    // ── framework-level errors keep the one-error-format (ProblemDetail) ─────────

    @Test
    void badUuidPathYields400ProblemDetail() throws Exception {
        // "not-a-uuid" cannot bind to @PathVariable UUID; the advice must still answer
        // with application/problem+json (not Spring's default /error JSON shape).
        mockMvc.perform(get("/api/v1/pets/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));
    }

    @Test
    void malformedJsonBodyYields400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/pets")
                        .with(jwtFor(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void updateWithAdminOnlyStatusYields400() throws Exception {
        when(petService.updatePet(eq(PET_ID), eq(OWNER_ID), any()))
                .thenThrow(new InvalidPetStatusException(PetStatus.PENDING_REVIEW));

        PetUpdateRequest req = new PetUpdateRequest(
                "Updated title", "Golden Retriever", 4,
                new BigDecimal("16000.00"), "Updated desc",
                "Meycauayan", "Bulacan", "MALE", PetStatus.PENDING_REVIEW);

        mockMvc.perform(put("/api/v1/pets/{id}", PET_ID)
                        .with(jwtFor(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid listing status"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    // ── authentication gates ───────────────────────────────────────────────────

    @Test
    void createPetRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePetRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/pets/{id}", PET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletePetRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/pets/{id}", PET_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myPetsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/pets"))
                .andExpect(status().isUnauthorized());
    }

    // ── method security (@PreAuthorize): role-based access ─────────────────────

    @Test
    void createPetIsAllowedForAUserRole() throws Exception {
        // A brand-new account is USER, and it MUST be able to post its first listing -
        // posting is exactly what promotes it to SELLER. So create allows USER.
        when(petService.createPet(eq(OWNER_ID), any())).thenReturn(fakeResponse());

        PetCreateRequest req = new PetCreateRequest(
                "Golden Retriever pup", Species.DOG, "Golden Retriever", 3,
                new BigDecimal("15000.00"), "Vaccinated, dewormed, playful.",
                "Meycauayan", "Bulacan", "MALE");

        mockMvc.perform(post("/api/v1/pets")
                        .with(jwtFor(OWNER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void createPetIsForbiddenForAnUnknownRole() throws Exception {
        PetCreateRequest req = new PetCreateRequest(
                "Golden Retriever pup", Species.DOG, "Golden Retriever", 3,
                new BigDecimal("15000.00"), null,
                "Meycauayan", "Bulacan", "MALE");

        mockMvc.perform(post("/api/v1/pets")
                        .with(jwtFor(OWNER_ID, "GUEST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                // 9c: the denial keeps the RFC 9457 shape (before the handler this body was empty).
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"));

        // The gate rejected the request before any business logic ran.
        org.mockito.Mockito.verifyNoInteractions(petService);
    }

    @Test
    void updateByNonSellerYields403ProblemDetail() throws Exception {
        // A USER token passes authentication but fails hasRole('SELLER').
        PetUpdateRequest req = new PetUpdateRequest(
                "Updated title", "Golden Retriever", 4,
                new BigDecimal("16000.00"), "Updated desc",
                "Meycauayan", "Bulacan", "MALE", PetStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/pets/{id}", PET_ID)
                        .with(jwtFor(OWNER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"));

        org.mockito.Mockito.verifyNoInteractions(petService);
    }

    @Test
    void deleteByNonSellerYields403() throws Exception {
        mockMvc.perform(delete("/api/v1/pets/{id}", PET_ID)
                        .with(jwtFor(OWNER_ID, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"));

        org.mockito.Mockito.verifyNoInteractions(petService);
    }

    // ── ownership check ────────────────────────────────────────────────────────

    @Test
    void updateByNonOwnerYields403() throws Exception {
        when(petService.updatePet(eq(PET_ID), eq(OTHER_ID), any()))
                .thenThrow(new ForbiddenPetAccessException(PET_ID));

        PetUpdateRequest req = new PetUpdateRequest(
                "Updated title", "Golden Retriever", 4,
                new BigDecimal("16000.00"), "Updated desc",
                "Meycauayan", "Bulacan", "MALE", PetStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/pets/{id}", PET_ID)
                        .with(jwtFor(OTHER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void deleteByNonOwnerYields403() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenPetAccessException(PET_ID))
                .when(petService).deletePet(PET_ID, OTHER_ID);

        mockMvc.perform(delete("/api/v1/pets/{id}", PET_ID)
                        .with(jwtFor(OTHER_ID)))
                .andExpect(status().isForbidden());
    }

    // ── create happy-path ──────────────────────────────────────────────────────

    @Test
    void createPetReturns201WithLocationHeader() throws Exception {
        when(petService.createPet(eq(OWNER_ID), any())).thenReturn(fakeResponse());

        PetCreateRequest req = new PetCreateRequest(
                "Golden Retriever pup", Species.DOG, "Golden Retriever", 3,
                new BigDecimal("15000.00"), "Vaccinated, dewormed, playful.",
                "Meycauayan", "Bulacan", "MALE");

        mockMvc.perform(post("/api/v1/pets")
                        .with(jwtFor(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/pets/" + PET_ID))
                .andExpect(jsonPath("$.id").value(PET_ID.toString()));
    }

    // ── input validation ───────────────────────────────────────────────────────

    @Test
    void createPetWithBlankTitleYields400() throws Exception {
        PetCreateRequest req = new PetCreateRequest(
                "", Species.DOG, "Golden Retriever", 3,
                new BigDecimal("15000.00"), null,
                "Meycauayan", "Bulacan", null);

        mockMvc.perform(post("/api/v1/pets")
                        .with(jwtFor(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    // ── delete happy-path ──────────────────────────────────────────────────────

    @Test
    void deleteByOwnerReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/pets/{id}", PET_ID)
                        .with(jwtFor(OWNER_ID)))
                .andExpect(status().isNoContent());

        verify(petService).deletePet(PET_ID, OWNER_ID);
    }

    // ── my pets ────────────────────────────────────────────────────────────────

    @Test
    void myPetsReturnsOwnersListings() throws Exception {
        when(petService.getMyPets(eq(OWNER_ID), any()))
                .thenReturn(new PagedResponse<>(List.of(fakeSummary()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/me/pets")
                        .with(jwtFor(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(PET_ID.toString()));
    }
}
