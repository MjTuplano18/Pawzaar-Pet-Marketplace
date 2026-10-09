package com.pawzaar.user.dto;

/**
 * What a successful login returns.
 *
 * <p>tokenType is always "Bearer": the client must send
 * {@code Authorization: Bearer <accessToken>} on every subsequent request. Storing the type in
 * the response (instead of assuming it) is the OAuth 2 convention.
 *
 * <p>expiresInSeconds lets the client know exactly when to expect a 401 and refresh, without
 * parsing the token itself.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static LoginResponse bearer(String token, long expiresInSeconds) {
        return new LoginResponse(token, "Bearer", expiresInSeconds);
    }
}
