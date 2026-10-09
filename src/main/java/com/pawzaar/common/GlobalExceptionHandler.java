package com.pawzaar.common;

// === our own domain exceptions (thrown by services, caught here) ===
import com.pawzaar.pet.ForbiddenPetAccessException;           // "not your listing"        -> 403
import com.pawzaar.pet.InvalidPetStatusException;           // "admin-only status"       -> 400
import com.pawzaar.pet.PetNotFoundException;               // "no pet with that id"     -> 404
import com.pawzaar.user.EmailAlreadyRegisteredException;   // "email already taken"     -> 409
import com.pawzaar.user.InvalidCredentialsException;       // "login failed"            -> 401
import com.pawzaar.user.InvalidRefreshTokenException;      // "refresh token bad"       -> 401

// === Spring's HTTP layer ===
import org.springframework.dao.OptimisticLockingFailureException;  // @Version race        -> 409
import org.springframework.security.access.AccessDeniedException;  // @PreAuthorize denied -> 403
import org.springframework.http.HttpStatus;      // enum of HTTP codes: NOT_FOUND, CONFLICT, BAD_REQUEST...
import org.springframework.http.ProblemDetail;   // Spring's built-in RFC 9457 "problem details" object
import org.springframework.http.converter.HttpMessageNotReadableException;  // malformed JSON -> 400
import org.springframework.web.HttpMediaTypeNotSupportedException;          // wrong content-type -> 415
import org.springframework.web.HttpRequestMethodNotSupportedException;      // wrong verb     -> 405
import org.springframework.web.bind.MethodArgumentNotValidException;  // thrown by @Valid failures
import org.springframework.web.bind.annotation.ExceptionHandler;     // "this exception -> this method"
import org.springframework.web.bind.annotation.RestControllerAdvice;  // applies to ALL controllers
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;  // bad UUID -> 400

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

    // 403 Forbidden: the authenticated user is not the owner of the listing.
    // Using 403 (not 404) here because the pet was already confirmed to exist
    // before the ownership check runs (see PetService.checkOwnership).
    @ExceptionHandler(ForbiddenPetAccessException.class)
    public ProblemDetail handleForbiddenPetAccess(ForbiddenPetAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,          // 403
                "You do not have permission to modify this listing"
        );
        problem.setTitle("Access denied");
        return problem;
    }

    // 403 Forbidden: a method-security check (@PreAuthorize) rejected the call.
    // Catching it HERE matters: without this handler the exception escapes MVC and Spring
    // Security's filter chain answers with an EMPTY body - breaking the "one error format" rule.
    // This keeps the 403 in the same RFC 9457 shape as every other error.
    // (Access denials raised by the URL rules in SecurityConfig happen BEFORE MVC reaches the
    //  controller, so this advice cannot see those - only method-security ones.)
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,          // 403
                "You do not have permission to perform this action");
        problem.setTitle("Access denied");
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

    // 401 Unauthorized: login failed. Note what is NOT here: no email, no "user not found"
    // vs "wrong password" distinction. Telling an attacker WHICH half was wrong would let
    // them enumerate registered accounts, so every failure returns the same vague message.
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,       // 401
                ex.getMessage()                // "Invalid email or password"
        );
        problem.setTitle("Invalid credentials");
        return problem;
    }

    // 400 Bad Request: a seller tried to set a status only an administrator may set.
    @ExceptionHandler(InvalidPetStatusException.class)
    public ProblemDetail handleInvalidPetStatus(InvalidPetStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,        // 400
                ex.getMessage());              // "Status PENDING_REVIEW cannot be set by a seller"
        problem.setTitle("Invalid listing status");
        problem.setProperty("status", ex.getStatus().name());
        return problem;
    }

    // 401 Unauthorized: POST /auth/refresh or /auth/logout was given a refresh token that is
    // unknown, expired, or already revoked. Same generic message as a bad login, for the same
    // reason: never help an attacker tell the cases apart.
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,       // 401
                ex.getMessage());              // "Refresh token is invalid or expired"
        problem.setTitle("Invalid refresh token");
        return problem;
    }

    // ── Framework-level errors, routed here so EVERY response keeps the same shape ──────────
    // Without the handlers below, these are raised by Spring MVC BEFORE any controller runs,
    // and would be answered by the default /error page: a different JSON shape and the wrong
    // content-type (application/json instead of application/problem+json). The five handlers
    // below close that gap and are why the "one error format" rule actually holds.

    // 400 Bad Request: a path variable / query parameter could not be converted to its target
    // type - e.g. GET /api/v1/pets/not-a-uuid (expected a UUID).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,        // 400
                "The value '" + ex.getValue() + "' is not valid for '" + ex.getName() + "'");
        problem.setTitle("Invalid request parameter");
        return problem;
    }

    // 400 Bad Request: the JSON body could not be parsed or mapped - malformed JSON, a
    // value of the wrong type, or an unknown enum value (e.g. species:"DRAGON").
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,        // 400
                "The request body is missing or malformed");
        problem.setTitle("Malformed request body");
        return problem;
    }

    // 405 Method Not Allowed: the URL exists but not for this verb (e.g. PATCH /api/v1/pets).
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, // 405
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint");
        problem.setTitle("Method not allowed");
        if (ex.getSupportedHttpMethods() != null) {
            problem.setProperty("allowedMethods", ex.getSupportedHttpMethods().stream()
                    .map(Object::toString)
                    .toList());
        }
        return problem;
    }

    // 415 Unsupported Media Type: wrong Content-Type (e.g. text/plain instead of application/json).
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, // 415
                "Content type '" + ex.getContentType() + "' is not supported; use application/json");
        problem.setTitle("Unsupported media type");
        return problem;
    }

    // 409 Conflict: two requests changed the same row at once and the @Version column detected
    // the race (optimistic locking). The client should re-read the resource and retry.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,           // 409
                "The resource was modified by someone else; reload it and try again");
        problem.setTitle("Concurrent modification");
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
