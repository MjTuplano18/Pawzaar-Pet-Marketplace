package com.pawzaar.user.dto;

// @NotBlank: not null, not "", and not just spaces  -> "you must actually send something".
import jakarta.validation.constraints.NotBlank;

// @Size: length limits.
import jakarta.validation.constraints.Size;

// @Pattern: your own regex, for formats the others do not cover (here: PH mobile numbers).
import jakarta.validation.constraints.Pattern;

/**
 * The JSON body we ACCEPT for PUT /api/v1/me - the shape of a full profile update.
 *
 * <p>Why PUT and not PATCH? PUT means "here is the ENTIRE new profile, replace mine with it".
 * That is unambiguous: {@code displayName} is always required, and {@code phone}/{@code bio} are
 * optional — send null or "" to clear them. A PATCH ("change only the fields I send") needs to
 * tell "field absent" apart from "field null", which is awkward with Java records, so we keep
 * the simple, standard semantics instead. The client already has the current values from
 * GET /api/v1/me, so it can send them back unchanged.
 *
 * <p>The same rules as registration: phone must be empty or a valid PH mobile number, bio is a
 * free-text line capped at 500 characters. The controller runs these (@Valid) BEFORE the
 * service is called, so bad input dies fast with 400 and never reaches the database.
 */
public record UpdateProfileRequest(

        @NotBlank(message = "displayName is required")
        @Size(max = 100, message = "displayName must be at most 100 characters")
        String displayName,

        // Same contract as RegisterRequest: empty string = "no phone", otherwise +639XXXXXXXXX.
        @Pattern(regexp = "^$|^\\+63\\d{9,10}$", message = "phone must be empty or +639XXXXXXXXX")
        String phone,

        @Size(max = 500, message = "bio must be at most 500 characters")
        String bio
) {
}