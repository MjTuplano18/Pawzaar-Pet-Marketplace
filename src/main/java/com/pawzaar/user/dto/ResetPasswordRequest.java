package com.pawzaar.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The JSON body accepted by {@code POST /api/v1/auth/reset-password}: the raw token taken from the
 * emailed link, plus the new password.
 *
 * <p>Both components are secrets (the token is a credential, the password is the credential), so
 * like {@link RegisterRequest} they are masked in {@code toString()} to keep them out of any
 * accidental {@code log.info(request)}.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "token is required")
        @Size(max = 512, message = "token is too long")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 72, message = "newPassword must be between 8 and 72 characters")
        String newPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=****, newPassword=****]";
    }
}
