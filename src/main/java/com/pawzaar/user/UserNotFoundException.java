package com.pawzaar.user;

import java.util.UUID;

import lombok.Getter;

/**
 * Thrown when an operation needs a user that no longer exists - for example the account was
 * deleted AFTER the access token was issued, so {@code sub} in the JWT points at nothing.
 *
 * <p>A domain exception, like the rest of the family: it carries no HTTP knowledge. The
 * {@code GlobalExceptionHandler} turns it into a 404.
 *
 * <p>In practice this is a "should never happen" path after login, but the code must still
 * answer a clean 404 instead of a NullPointerException if the row is gone.
 */
@Getter
public class UserNotFoundException extends RuntimeException {

    private final UUID userId;

    public UserNotFoundException(UUID userId) {
        super("No user exists with id " + userId);   // message shown to the API client
        this.userId = userId;
    }
}