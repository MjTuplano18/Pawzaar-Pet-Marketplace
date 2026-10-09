package com.pawzaar.common;

import com.pawzaar.common.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused test for the 429 mapping: status code, the Retry-After header, and the problem body.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rateLimitExceededBecomes429WithRetryAfterHeader() {
        ResponseEntity<ProblemDetail> response =
                handler.handleRateLimitExceeded(new RateLimitExceededException(Duration.ofSeconds(7)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("7", response.getHeaders().getFirst("Retry-After"));
        assertEquals("Too many requests", response.getBody().getTitle());
        assertEquals(429, response.getBody().getStatus());
    }

    @Test
    void subSecondWaitRoundsUpToOneSecond() {
        ResponseEntity<ProblemDetail> response =
                handler.handleRateLimitExceeded(new RateLimitExceededException(Duration.ofMillis(200)));

        assertEquals("1", response.getHeaders().getFirst("Retry-After"));
    }
}
