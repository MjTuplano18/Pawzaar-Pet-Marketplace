package com.pawzaar.user;

import java.util.UUID;

import lombok.Getter;

/**
 * Thrown by GET /api/v1/me/avatar when the user has never set a profile picture.
 *
 * <p>Distinct from {@link UserNotFoundException}: the user EXISTS, but the avatar does not. The
 * {@code GlobalExceptionHandler} maps it to 404 just like the other "not found" family.
 */
@Getter
public class AvatarNotFoundException extends RuntimeException {

    private final UUID userId;

    public AvatarNotFoundException(UUID userId) {
        super("User " + userId + " has no avatar");   // message shown to the API client
        this.userId = userId;
    }
}