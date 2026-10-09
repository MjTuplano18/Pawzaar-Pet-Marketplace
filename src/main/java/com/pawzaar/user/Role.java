package com.pawzaar.user;

/**
 * An ENUM = a fixed list of allowed values. This one answers the question
 * "what kind of user is this?" and nothing else.
 *
 * <p>Why an enum and not a String? Because it makes invalid states impossible to write:
 * <pre>
 *   user.setRole("SUPER_ADMIN");   // compile error: not a Role
 *   user.setRole(Role.SELLER);     // fine
 * </pre>
 * The compiler protects you instead of your code review.
 *
 * <p>Stored in the database as the constant NAME (USER / SELLER), because the entity uses
 * @Enumerated(EnumType.STRING) and the column is VARCHAR(20).
 *
 * <p>Kept minimal on purpose: in Pawzaar EVERYONE can buy, and a seller is just a user who
 * has posted a pet (that rule lives in the service, not here).
 * A real system with dozens of roles would use a users-to-roles TABLE instead of one column.
 */
public enum Role {
    USER,     // the default for every new account (see User.register -> role defaults here)
    SELLER,   // granted when a user posts their first pet; the V3 seed row already uses it
    ADMIN     // can hide listings, ban users, and manage reports
}
