package com.pawzaar.common.ratelimit;

import java.time.Duration;

/**
 * Thrown when a client has sent more requests than its bucket allows in the current window.
 *
 * <p>Like every other domain exception in Pawzaar, it says what went wrong in plain business
 * language and knows nothing about HTTP. {@code GlobalExceptionHandler} decides the status code
 * (429 Too Many Requests) and adds the {@code Retry-After} header.
 *
 * <p>It carries the wait time so the handler can tell the client exactly how long to back off.
 */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        // Round up to whole seconds for a friendly message; always at least 1s.
        super("Too many requests; retry in " + Math.max(1, retryAfter.toSeconds()) + "s");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
