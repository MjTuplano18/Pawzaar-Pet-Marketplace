package com.pawzaar.user;

/**
 * Thrown when login fails - wrong email, wrong password, or an account that does not exist.
 *
 * <p>SECURITY BY DESIGN: all three cases throw the SAME exception with the SAME message.
 * If we answered "no such email" for one case and "wrong password" for another, an attacker
 * could probe our database to discover which addresses have accounts (user enumeration).
 * A vague message is a feature, not a bug.
 *
 * <p>The email is kept internally so the server could LOG the failed attempt - it is
 * deliberately NOT exposed to the client, so the response leaks nothing.
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
