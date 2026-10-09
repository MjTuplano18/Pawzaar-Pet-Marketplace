package com.pawzaar.user.controller;

import com.pawzaar.user.dto.LoginRequest;
import com.pawzaar.user.dto.LoginResponse;
import com.pawzaar.user.dto.RegisterRequest;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Exposes the auth feature over HTTP.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/auth/register -> 201 + the new user (no password, ever)</li>
 *   <li>POST /api/v1/auth/login    -> 200 + a JWT the client sends as "Authorization: Bearer ..."</li>
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

    // Login returns 200 with the token, NOT 201: nothing was created in the database.
    // Wrong credentials never reach this method's logic - AuthService throws
    // InvalidCredentialsException, which the GlobalExceptionHandler renders as 401.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

}
