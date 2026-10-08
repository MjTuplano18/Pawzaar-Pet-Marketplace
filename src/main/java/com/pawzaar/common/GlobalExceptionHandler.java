package com.pawzaar.common;

// === our own domain exceptions (thrown by services, caught here) ===
import com.pawzaar.pet.PetNotFoundException;               // "no pet with that id"     -> 404
import com.pawzaar.user.EmailAlreadyRegisteredException;   // "email already taken"     -> 409

// === Spring's HTTP layer ===
import org.springframework.http.HttpStatus;      // enum of HTTP codes: NOT_FOUND, CONFLICT, BAD_REQUEST...
import org.springframework.http.ProblemDetail;   // Spring's built-in RFC 9457 "problem details" object
import org.springframework.web.bind.MethodArgumentNotValidException;  // thrown by @Valid failures
import org.springframework.web.bind.annotation.ExceptionHandler;     // "this exception -> this method"
import org.springframework.web.bind.annotation.RestControllerAdvice;  // applies to ALL controllers

import java.util.List;  // needed for the errors array we attach to validation problems

/**
 * The ONE place where exceptions become HTTP responses (RFC 9457 "problem details").
 *
 * <p>How the flow works when something goes wrong:
 * <pre>
 *   AuthService throws EmailAlreadyRegisteredException
 *        -> nobody catches it inside the app (we WANT that: no try/catch noise)
 *        -> Spring sees an @ExceptionHandler for that exact type
 *        -> calls our method, gets a ProblemDetail back
 *        -> serializes it as application/problem+json with the matching status code
 * </pre>
 *
 * <p>Because this is an ADVICE (@RestControllerAdvice), it wraps every controller in the app.
 * Adding a new endpoint requires ZERO error-handling code: you just throw, and the format
 * stays identical. That is the whole point of the "one error format" architecture rule.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Whenever ANY controller/service throws PetNotFoundException, Spring routes it here.
    @ExceptionHandler(PetNotFoundException.class)
    public ProblemDetail handlePetNotFound(PetNotFoundException ex) {
        // forStatusAndDetail builds the object and sets "status" + "detail" in one call.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,          // 404
                ex.getMessage()                // "No pet exists with id 123..."
        );
        problem.setTitle("Pet not found");              // short human-readable summary
        problem.setProperty("petId", ex.getPetId());   // extra machine-readable field
        return problem;
    }

    // 409 Conflict: the request was well-formed, but it clashes with existing state.
    // (Contrast: 400 = malformed input, 401 = who are you, 403 = we know you, but no,
    //           404 = the thing does not exist.)
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,           // 409
                ex.getMessage()                // "An account with email 'x' already exists"
        );
        problem.setTitle("Email already registered");
        problem.setProperty("email", ex.getEmail());
        return problem;
    }

    // 400 Bad Request: @Valid rejected the body (a @NotBlank / @Email / @Size / @Pattern failed).
    // This ONE handler covers every @Valid failure in the entire app - that is how the
    // "single error format" rule scales without touching this file again.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,        // 400
                "One or more fields are invalid"
        );
        problem.setTitle("Validation failed");

        // getBindingResult().getFieldErrors() = the list of every failed field.
        // We stream it into a compact JSON array: [{"field": "email", "message": "..."}, ...]
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldProblem(fe.getField(), String.valueOf(fe.getDefaultMessage())))
                .toList());

        return problem;
    }

    /**
     * A tiny nested record used only to shape the "errors" array in the JSON.
     * Records can be declared INSIDE a class - handy for small internal value objects
     * that have no reason to be public API.
     */
    record FieldProblem(String field, String message) {
    }

}
