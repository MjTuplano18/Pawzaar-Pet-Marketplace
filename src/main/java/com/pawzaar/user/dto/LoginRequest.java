package com.pawzaar.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * The JSON body we ACCEPT for login: an email and a plaintext password.
 *
 * <p>The password travels in the request body, never in the URL - URLs end up in access logs,
 * browser history and proxy caches. That is why login is a POST body and not a query string.
 */
public record LoginRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
    // Mask the password if this object is ever logged.
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
