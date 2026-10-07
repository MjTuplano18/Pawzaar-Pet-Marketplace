package com.pawzaar.pet;

import java.util.UUID;
import lombok.Getter;
/**
 * Thrown when a pet with the given id does not exist.
 * A domain (business) exception: it describes "what went wrong" in the pet feature,
 * with no knowledge of HTTP or JSON - the GlobalExceptionHandler decides how to
 * translate it to a response.
 *
 * <p>Extends RuntimeException ("unchecked") on purpose: the compiler does NOT force
 * callers to catch it, so it can travel up from the service to the advice without
 * polluting every method signature with `throws`.
 */

@Getter
public class PetNotFoundException extends RuntimeException {

    private final UUID petId;

    public PetNotFoundException(UUID petId) {
        super("No pet exists with id " + petId);   // message shown to the API client
        this.petId = petId;
    }


}