package com.pawzaar.common;

import com.pawzaar.pet.PetNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The ONE place where exceptions become HTTP responses (RFC 9457 "problem details").
 *
 * <p>Every controller in the app is automatically wrapped by this advice - controllers
 * stay thin because they never write error code themselves: they just throw, or let
 * exceptions bubble up.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Whenever ANY controller throws PetNotFoundException, Spring routes it here.
    @ExceptionHandler(PetNotFoundException.class)
    public ProblemDetail handlePetNotFound(PetNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,          // 404
                ex.getMessage()                // "No pet exists with id 123..."
        );
        problem.setTitle("Pet not found");     // short human-readable summary
        problem.setProperty("petId", ex.getPetId());  // extra machine-readable field
        return problem;
    }
}