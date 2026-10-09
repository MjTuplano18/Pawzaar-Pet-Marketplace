package com.pawzaar.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /api/v1/auth/refresh} and {@code POST /api/v1/auth/logout}: the opaque
 * refresh token the client received at login.
 *
 * <p>It travels in the request body, never the URL (URLs leak into access logs and history).
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=****]";
    }
}
