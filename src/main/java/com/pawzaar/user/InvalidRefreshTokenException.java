package com.pawzaar.user;

/**
 * Thrown when a refresh token is unknown, expired, or already used/revoked.
 * The {@code GlobalExceptionHandler} converts this to HTTP 401.
 *
 * <p>The message is deliberately generic: telling a caller <em>why</em> a token was rejected
 * (unknown vs expired vs revoked) helps an attacker probe token state, so all cases look alike.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
