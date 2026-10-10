package com.pawzaar.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The JSON body accepted by {@code POST /api/v1/auth/forgot-password}: the address to send a reset
 * link to.
 *
 * <p>The response is the same whether or not the address is registered, so this DTO carries no
 * secret and needs no masked {@code toString()}.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email
) {
}
