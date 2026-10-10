package com.pawzaar.user.controller;

import com.pawzaar.user.dto.UpdateProfileRequest;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The authenticated user's OWN profile, under {@code /api/v1/me}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/me -> 200 + the caller's profile (no password, ever)</li>
 *   <li>PUT /api/v1/me -> 200 + the updated profile (displayName, phone, bio)</li>
 * </ul>
 *
 * <p>Both require a valid access token (the default "anyRequest().authenticated()" rule in
 * SecurityConfig). The identity comes from {@code @AuthenticationPrincipal Jwt}: Spring Security
 * hands us the VERIFIED token, and its {@code sub} claim is the user id — so the controller never
 * trusts a body field or header for "who am I".
 *
 * <p>THE THIN CONTROLLER RULE: parse the request, delegate to the service, return the DTO.
 */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final AuthService authService;

    public ProfileController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.getProfile(userId(jwt));
    }

    // @Valid runs the UpdateProfileRequest checks (displayName required, phone format, bio length)
    // here at the edge, before the service ever runs - a 400 arrives without touching the DB.
    @PutMapping
    public UserResponse update(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(userId(jwt), request);
    }

    /** The JWT subject ("sub") is the user id we stamped in when issuing the token. */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}