package com.pawzaar.pet;

import java.util.UUID;

/**
 * Thrown when an authenticated user tries to modify a pet listing they do not own.
 * The GlobalExceptionHandler converts this to HTTP 403 Forbidden.
 *
 * <p>The message deliberately does not say "you are not the owner" vs "this pet does not
 * exist" - that distinction could help an attacker enumerate other users' listings.
 */
public class ForbiddenPetAccessException extends RuntimeException {

    private final UUID petId;

    public ForbiddenPetAccessException(UUID petId) {
        super("Access to pet " + petId + " is not allowed");
        this.petId = petId;
    }

    public UUID getPetId() {
        return petId;
    }
}
