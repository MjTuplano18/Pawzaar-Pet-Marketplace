package com.pawzaar.common;

/**
 * A per-account limit was hit: too many listings, or too many images on one listing (H6).
 *
 * <p>Maps to {@code 409 Conflict}. The request is well-formed and the caller is authorized; it
 * simply conflicts with the state the account is already in. {@code 409} (rather than {@code 403})
 * tells the client "you have reached a cap", not "you may never do this".
 */
public class QuotaExceededException extends RuntimeException {

    private final String resource;
    private final int limit;

    public QuotaExceededException(String resource, int limit) {
        super("You have reached the maximum of " + limit + " " + resource);
        this.resource = resource;
        this.limit = limit;
    }

    public String getResource() {
        return resource;
    }

    public int getLimit() {
        return limit;
    }
}
