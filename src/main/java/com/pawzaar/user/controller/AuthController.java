package com.pawzaar.user.controller;

import com.pawzaar.user.dto.RegisterRequest;  // the JSON body we accept
import com.pawzaar.user.dto.UserResponse;     // the JSON body we return
import com.pawzaar.user.service.AuthService;   // the business layer we delegate to

// @Valid = "run the validation annotations on the record BEFORE you call the service".
// Violations throw MethodArgumentNotValidException -> GlobalExceptionHandler -> 400 ProblemDetail.
import jakarta.validation.Valid;

// ResponseEntity = full control over the response: status code, headers AND body.
import org.springframework.http.ResponseEntity;

// @PostMapping("/register") = this method answers HTTP POST /register.
import org.springframework.web.bind.annotation.PostMapping;

// @RequestBody = "take the raw JSON body and turn it into that record".
import org.springframework.web.bind.annotation.RequestBody;

// @RequestMapping("/api/v1/auth") = every method in this class lives under this prefix.
import org.springframework.web.bind.annotation.RequestMapping;

// @RestController = @Controller + @ResponseBody: return values are serialized to JSON directly.
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;  // used to build the Location header of a 201 response

/**
 * Exposes the auth feature over HTTP.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/auth/register -> 201 + the new user (no password, ever)</li>
 * </ul>
 *
 * <p>THE THIN CONTROLLER RULE: parse the request, delegate to the service, return the DTO.
 * No business logic, no repository calls, no hashing, no transactions in here.
 * If you ever find yourself writing an `if` about business rules in a controller,
 * it belongs in the service instead.
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
        // (a URL for the thing you just created - useful for clients and for future GET endpoints)
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.id()))
                .body(created);
    }

}
