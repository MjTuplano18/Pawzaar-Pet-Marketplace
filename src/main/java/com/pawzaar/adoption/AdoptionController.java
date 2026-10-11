package com.pawzaar.adoption;

import com.pawzaar.adoption.dto.AdoptionRequestResponse;
import com.pawzaar.adoption.dto.CreateAdoptionRequestRequest;
import com.pawzaar.adoption.dto.UpdateAdoptionStatusRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for the adoption-request feature.
 *
 * <p>All endpoints require authentication (covered by SecurityConfig's anyRequest().authenticated()).
 *
 * <ul>
 *   <li>POST  /api/v1/pets/{petId}/adoption-requests     → 201 + new request</li>
 *   <li>GET   /api/v1/pets/{petId}/adoption-requests     → 200 + list (pet owner only)</li>
 *   <li>GET   /api/v1/me/adoption-requests               → 200 + requester's own requests</li>
 *   <li>GET   /api/v1/me/received-requests               → 200 + all requests for seller's pets</li>
 *   <li>PATCH /api/v1/adoption-requests/{id}             → 200 + updated request</li>
 *   <li>GET   /api/v1/adoption-requests/{id}             → 200 + single request</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class AdoptionController {

    private final AdoptionService adoptionService;

    public AdoptionController(AdoptionService adoptionService) {
        this.adoptionService = adoptionService;
    }

    /** Submit a new adoption request for a pet. Returns 201 Created. */
    @PostMapping("/pets/{petId}/adoption-requests")
    public ResponseEntity<AdoptionRequestResponse> createRequest(
            @PathVariable UUID petId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAdoptionRequestRequest body) {
        AdoptionRequestResponse response = adoptionService.createRequest(petId, userId(jwt), body);
        return ResponseEntity.status(201).body(response);
    }

    /** All requests for a specific pet. Only the pet owner may call this. */
    @GetMapping("/pets/{petId}/adoption-requests")
    public List<AdoptionRequestResponse> getRequestsForPet(
            @PathVariable UUID petId,
            @AuthenticationPrincipal Jwt jwt) {
        return adoptionService.getRequestsForPet(petId, userId(jwt));
    }

    /** All adoption requests submitted by the authenticated user. */
    @GetMapping("/me/adoption-requests")
    public List<AdoptionRequestResponse> getMyRequests(
            @AuthenticationPrincipal Jwt jwt) {
        return adoptionService.getMyRequests(userId(jwt));
    }

    /** All requests received across every pet the authenticated seller owns. */
    @GetMapping("/me/received-requests")
    public List<AdoptionRequestResponse> getReceivedRequests(
            @AuthenticationPrincipal Jwt jwt) {
        return adoptionService.getReceivedRequests(userId(jwt));
    }

    /** Update the status of a specific adoption request (APPROVE / REJECT / WITHDRAW). */
    @PatchMapping("/adoption-requests/{id}")
    public AdoptionRequestResponse updateStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAdoptionStatusRequest body) {
        return adoptionService.updateStatus(id, userId(jwt), body);
    }

    /** Get a single adoption request by id. */
    @GetMapping("/adoption-requests/{id}")
    public AdoptionRequestResponse getRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return adoptionService.getRequest(id, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
