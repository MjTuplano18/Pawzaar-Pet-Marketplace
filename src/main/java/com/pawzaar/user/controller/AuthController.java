package com.pawzaar.user.controller;

import com.pawzaar.user.dto.LoginRequest;
import com.pawzaar.user.dto.RefreshRequest;
import com.pawzaar.user.dto.RegisterRequest;
import com.pawzaar.user.dto.TokenResponse;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Exposes the auth feature over HTTP.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/auth/register -> 201 + the new user (no password, ever)</li>
 *   <li>POST /api/v1/auth/login    -> 200 + an access JWT AND a refresh token</li>
 *   <li>POST /api/v1/auth/refresh  -> 200 + a brand-new token pair (rotates the refresh token)</li>
 *   <li>POST /api/v1/auth/logout   -> 204, revokes the refresh token</li>
 * </ul>
 *
 * <p>THE THIN CONTROLLER RULE: parse the request, delegate to the service, return the DTO.
 * No business logic, no repository calls, no hashing, no token signing in here.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // The two annotations that do the real work:
    //   @RequestBody - Jackson reads the JSON {"email": "...", ...} into the RegisterRequest record
    //   @Valid       - Spring checks @NotBlank / @Email / @Size / @Pattern and stops here with 400
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = authService.register(request);

        // 201 Created is the industry-correct answer for "I made something new".
        // .created(...) also sets the Location header: /api/v1/users/<new id>
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.id()))
                .body(created);
    }

    // Login returns 200 with the tokens, NOT 201: nothing was created in the database.
    // Wrong credentials never reach this method's logic - AuthService throws
    // InvalidCredentialsException, which the GlobalExceptionHandler renders as 401.
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Exchanges the refresh token for a new pair. The old refresh token is burned (rotation), so
    // the client MUST store the new one it gets back. An unknown/expired/revoked token -> 401.
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    // 204 No Content: the request succeeded and there is nothing meaningful to return.
    // Unlike login/refresh, this is idempotent - logging out twice is not an error.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

}
