package com.pawzaar.pet;

/**
 * Thrown when the search filters are individually valid but inconsistent with each other - e.g. a
 * minimum price that is higher than the maximum, or a minimum age above the maximum. The
 * GlobalExceptionHandler turns it into HTTP 400.
 *
 * <p>Bean Validation can check one field at a time, but "min must not exceed max" is a cross-field
 * rule, so it lives here rather than on {@code PetFilter}'s fields.
 */
public class InvalidFilterException extends RuntimeException {

    private final String parameter;

    public InvalidFilterException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }

    public String getParameter() {
        return parameter;
    }
}
