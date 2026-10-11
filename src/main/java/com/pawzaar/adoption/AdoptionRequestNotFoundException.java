package com.pawzaar.adoption;

import lombok.Getter;

import java.util.UUID;

/**
 * Thrown when an adoption request with the given id does not exist.
 * The {@link com.pawzaar.common.GlobalExceptionHandler} converts this to HTTP 404.
 */
@Getter
public class AdoptionRequestNotFoundException extends RuntimeException {

    private final UUID requestId;

    public AdoptionRequestNotFoundException(UUID requestId) {
        super("No adoption request exists with id " + requestId);
        this.requestId = requestId;
    }
}
