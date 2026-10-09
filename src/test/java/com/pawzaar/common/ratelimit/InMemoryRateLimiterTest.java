package com.pawzaar.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the token bucket. No Spring, no database, no sleeping: a fake nanosecond clock
 * lets us jump forward in time instantly and deterministically.
 */
class InMemoryRateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    // The fake clock every limiter in this class reads from.
    private final AtomicLong now = new AtomicLong(0);

    private InMemoryRateLimiter limiter(int capacity, int refillTokens, Duration period) {
        // Generous ttl/maxKeys so refill tests are never affected by eviction.
        return new InMemoryRateLimiter(capacity, refillTokens, period,
                Duration.ofMinutes(10), 1000, now::get);
    }

    @Test
    void allowsUpToCapacityThenDenies() {
        InMemoryRateLimiter limiter = limiter(3, 3, MINUTE);

        assertTrue(limiter.tryConsume("1.1.1.1").allowed());
        assertTrue(limiter.tryConsume("1.1.1.1").allowed());
        assertTrue(limiter.tryConsume("1.1.1.1").allowed());
        assertFalse(limiter.tryConsume("1.1.1.1").allowed(), "4th request exceeds the burst");
    }

    @Test
    void deniedRequestReportsApproximateWait() {
        InMemoryRateLimiter limiter = limiter(1, 1, MINUTE);

        assertTrue(limiter.tryConsume("ip").allowed());

        InMemoryRateLimiter.Decision denied = limiter.tryConsume("ip");

        assertFalse(denied.allowed());
        // One token per minute -> about 60s to wait (we round up, so never less).
        assertTrue(denied.retryAfter().toSeconds() >= 59 && denied.retryAfter().toSeconds() <= 60,
                "unexpected retryAfter: " + denied.retryAfter());
    }

    @Test
    void tokensRefillOverTime() {
        InMemoryRateLimiter limiter = limiter(2, 2, MINUTE);

        assertTrue(limiter.tryConsume("ip").allowed());
        assertTrue(limiter.tryConsume("ip").allowed());
        assertFalse(limiter.tryConsume("ip").allowed(), "bucket is empty");

        now.addAndGet(MINUTE.toNanos());     // one full refill period later

        assertTrue(limiter.tryConsume("ip").allowed(), "refilled after the period");
        assertTrue(limiter.tryConsume("ip").allowed(), "two tokens are available again");
    }

    @Test
    void keysHaveIndependentBuckets() {
        InMemoryRateLimiter limiter = limiter(1, 1, MINUTE);

        assertTrue(limiter.tryConsume("1.1.1.1").allowed());
        assertFalse(limiter.tryConsume("1.1.1.1").allowed());
        // A different client must not be punished for the first one's traffic.
        assertTrue(limiter.tryConsume("2.2.2.2").allowed());
    }

    @Test
    void staleBucketsAreEvictedOnceTheMapGrowsPastItsLimit() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(1, 1, MINUTE,
                MINUTE, 2, now::get);

        assertTrue(limiter.tryConsume("A").allowed());
        assertFalse(limiter.tryConsume("A").allowed(), "A is now empty");
        assertEquals(1, limiter.trackedKeys());

        now.addAndGet(Duration.ofMinutes(5).toNanos());   // A is now stale

        limiter.tryConsume("B");
        limiter.tryConsume("C");      // size is now 3 > maxKeys(2)

        // A's stale bucket is dropped, so a fresh, full bucket is created for it.
        assertTrue(limiter.tryConsume("A").allowed(), "stale bucket should have been evicted");
    }

    @Test
    void rejectsZeroCapacityConfiguration() {
        assertThrowsForConfig(0, 1, MINUTE, "capacity");
    }

    @Test
    void rejectsZeroRefillConfiguration() {
        assertThrowsForConfig(1, 0, MINUTE, "refillTokens");
    }

    private static void assertThrowsForConfig(int capacity, int refillTokens, Duration period, String field) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new InMemoryRateLimiter(capacity, refillTokens, period,
                        Duration.ofMinutes(10), 100, System::nanoTime));
        assertTrue(ex.getMessage().contains(field));
    }
}
