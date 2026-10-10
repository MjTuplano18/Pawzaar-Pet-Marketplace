package com.pawzaar.user;

/**
 * Thrown when login fails - wrong email, wrong password, or an account that does not exist.
 *
 * <p>SECURITY BY DESIGN: all three cases throw the SAME exception with the SAME message.
 * If we answered "no such email" for one case and "wrong password" for another, an attacker
 * could probe our database to discover which addresses have accounts (user enumeration).
 * A vague message is a feature, not a bug.
 *
 * <p>The email is kept internally for the record but is deliberately NEVER logged or exposed -
 * neither the response nor the logs leak which addresses have accounts.
 */
public class InvalidCredentialsException extends RuntimeException {

    private final String email;

    public InvalidCredentialsException(String email) {
        super("Invalid email or password");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
