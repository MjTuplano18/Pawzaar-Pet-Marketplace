package com.pawzaar.user.dto;

// jakarta.validation = the Bean Validation API (the VALIDATION half of Spring).
// Remember: jakarta.* is the modern namespace; javax.validation is the old one.

// @NotBlank: not null, not "", and not just spaces  -> "you must actually send something".
import jakarta.validation.constraints.NotBlank;

// @Email: applies a real-world email regex (has an @, a domain, sensible characters).
import jakarta.validation.constraints.Email;

// @Size: length limits. Also checks NUMBERS (e.g. @Size(min = 8, max = 72)).
import jakarta.validation.constraints.Size;

// @Pattern: your own regex, for formats the others do not cover (here: PH mobile numbers).
import jakarta.validation.constraints.Pattern;

/**
 * The JSON body we ACCEPT for registration - the shape of the request.
 *
 * <p>A record gives us an immutable carrier plus a constructor, getters, equals, hashCode and
 * toString for free. Accessors have no "get" prefix: request.email(), not request.getEmail().
 *
 * <p>The annotations below are the API's CONTRACT: the controller runs them (@Valid) before
 * AuthService is ever called, so bad data is rejected with 400 and never reaches the database.
 *
 * <p>Note the 72-character password maximum. That is not a style choice: BCrypt silently
 * ignores everything after the first 72 bytes, so a longer password would be TRUNCATED
 * (a real vulnerability). We reject it instead.
 */
public record RegisterRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "displayName is required")
        @Size(max = 100, message = "displayName must be at most 100 characters")
        String displayName,

        // Breakdown of the regex:
        //   ^$            -> an EMPTY string is allowed (phone is optional)
        //   ^\+63\d{9,10}$ -> otherwise: a literal +, then country code 63, then 9-10 digits
        //   \\+ and \\d     -> in a Java string, a backslash must be doubled
        @Pattern(regexp = "^$|^\\+63\\d{9,10}$", message = "phone must be empty or +639XXXXXXXXX")
        String phone
) {
    // Records generate toString() automatically, and the default one would print the RAW
    // password - so a single log.info(request) would leak it into your logs forever.
    // We override it to keep credentials out. Small habit, real security benefit.
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=****]";
    }
}
