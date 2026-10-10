package com.pawzaar.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The JSON body accepted by {@code POST /api/v1/auth/verify-email}: the raw token taken from the
 * link that was emailed to the user.
 *
 * <p>The token is a secret, so like {@link RegisterRequest} it is masked in {@code toString()} to
 * keep it out of any accidental {@code log.info(request)}.
 */
public record VerifyEmailRequest(

        @NotBlank(message = "token is required")
        @Size(max = 512, message = "token is too long")
        String token
) {
    @Override
    public String toString() {
        return "VerifyEmailRequest[token=****]";
    }
}
