package com.pawzaar.user;

/**
 * Thrown when an email-verification token is unknown, expired, or already spent.
 * The {@code GlobalExceptionHandler} converts this to HTTP 400.
 *
 * <p>The message is deliberately generic: telling a caller <em>why</em> a token was rejected
 * (unknown vs expired vs used) helps an attacker probe token state, so all cases look alike - the
 * same reasoning as {@link InvalidRefreshTokenException}.
 */
public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException() {
        super("Verification token is invalid or expired");
    }
}
