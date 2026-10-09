package com.pawzaar.pet;

import java.util.Collection;

/**
 * Thrown when a client asks to sort the feed by an unsupported field or direction.
 * The GlobalExceptionHandler turns it into HTTP 400.
 *
 * <p>Why not just trust the client's field name? Spring Data would try to resolve ANY
 * property, throw PropertyReferenceException (a 500) for a typo, and happily sort by a
 * column we never intended to expose. An allowlist keeps it to a known, indexed set.
 */
public class InvalidSortException extends RuntimeException {

    private final String parameter;
    private final String value;

    public InvalidSortException(String parameter, String value, Collection<String> allowed) {
        super("'" + value + "' is not a valid value for '" + parameter
                + "'; allowed: " + String.join(", ", allowed));
        this.parameter = parameter;
        this.value = value;
    }

    public String getParameter() {
        return parameter;
    }

    public String getValue() {
        return value;
    }
}
