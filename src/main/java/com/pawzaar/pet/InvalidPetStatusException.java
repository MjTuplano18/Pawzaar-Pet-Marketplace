package com.pawzaar.pet;

/**
 * Thrown when a seller tries to move a listing into a status that only an administrator
 * may set (currently {@link PetStatus#PENDING_REVIEW}, used when a listing is flagged for
 * review). The {@code GlobalExceptionHandler} converts this to HTTP 400 Bad Request.
 *
 * <p>Why a dedicated exception instead of a plain {@code IllegalArgumentException}? Because
 * a specific type maps to a specific HTTP status in the one error handler, and it documents
 * the rule at the throw site.
 */
public class InvalidPetStatusException extends RuntimeException {

    private final PetStatus status;

    public InvalidPetStatusException(PetStatus status) {
        super("Status " + status + " cannot be set by a seller");
        this.status = status;
    }

    public PetStatus getStatus() {
        return status;
    }
}
