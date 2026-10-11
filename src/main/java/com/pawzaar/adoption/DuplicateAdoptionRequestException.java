package com.pawzaar.adoption;

/**
 * Thrown when a user attempts to create a second PENDING adoption request for the same pet.
 * The {@link com.pawzaar.common.GlobalExceptionHandler} converts this to HTTP 409.
 */
public class DuplicateAdoptionRequestException extends RuntimeException {

    public DuplicateAdoptionRequestException() {
        super("A pending adoption request already exists for this pet from this user");
    }
}
