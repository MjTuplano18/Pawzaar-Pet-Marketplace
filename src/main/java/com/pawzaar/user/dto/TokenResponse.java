package com.pawzaar.user.dto;

/**
 * The token pair returned by a successful {@code /auth/login} OR {@code /auth/refresh}.
 *
 * <p>The <b>access token</b> is a short-lived JWT the client sends as
 * {@code Authorization: Bearer <accessToken>}. It is verified by signature alone (no database
 * lookup), so it is fast but cannot be revoked - that is the price of statelessness.
 *
 * <p>The <b>refresh token</b> is an OPAQUE random string (not a JWT). The server stores only its
 * SHA-256 hash, so it CAN be revoked/rotated, and it is the only thing the client sends to
 * {@code /auth/refresh} to obtain a fresh pair. Treat it like a password: never log it.
 *
 * <p>{@code expiresInSeconds} describes the ACCESS token only; the refresh token lives for days.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }

    // Mask BOTH secrets if this object is ever logged. Access tokens are short-lived but a
    // refresh token in a log file is a long-lived credential leak.
    @Override
    public String toString() {
        return "TokenResponse[accessToken=****, refreshToken=****, tokenType=" + tokenType
                + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
