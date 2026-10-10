package com.pawzaar.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A thread-safe, in-memory <b>token bucket</b> rate limiter: one bucket per key (usually a client
 * IP address).
 *
 * <p>Why a token bucket? It is the standard way to allow a short <i>burst</i> while capping the
 * long-run rate. Each bucket starts full with {@code capacity} tokens; every request removes one.
 * Tokens trickle back at {@code refillTokens} per {@code refillPeriod}. A request with no token
 * left is denied, and we can compute exactly how long until the next one appears.
 *
 * <pre>
 *   capacity = 5, refill = 5 / minute
 *   5 quick requests  -> allowed (burst)
 *   6th               -> denied; retryAfter &lt;= 12s
 *   steady state      -> ~5 requests per minute
 * </pre>
 *
 * <p>Scope and limits (important to be honest about): this is <b>per instance</b>. Run five copies
 * behind a load balancer and each one counts separately. A shared counter (Redis) is the production
 * answer; for this single-instance portfolio project, in-memory is a deliberate, documented choice.
 *
 * <p><b>Memory safety (M8).</b> Buckets are keyed by client IP, which an attacker controls, so the
 * map could otherwise grow forever. It is held in a Caffeine cache with a <b>hard</b>
 * {@code maximumSize(maxKeys)} <i>and</i> {@code expireAfterAccess(bucketTtl)}: the size cap is the
 * real guarantee (a flood of fresh keys is bounded immediately), and the TTL additionally reclaims
 * keys that simply went quiet. Both run without a background thread — Caffeine maintains the cache
 * on access/write.
 *
 * <p>One honest caveat: because the cache is bounded, a key whose bucket is evicted gets a fresh,
 * full bucket next time. Cycling through many distinct keys can therefore sidestep per-key limiting
 * (that is inherent to any keyed limiter), but it can no longer exhaust memory — which is exactly
 * what the cap is for.
 */
public class InMemoryRateLimiter {

    /** The outcome of one attempt: whether it is allowed, and (if not) how long to wait. */
    public record Decision(boolean allowed, Duration retryAfter) {
    }

    private final int capacity;
    private final int refillTokens;
    private final Duration refillPeriod;
    private final LongSupplier nanoClock;
    private final Cache<String, Bucket> buckets;

    public InMemoryRateLimiter(int capacity, int refillTokens, Duration refillPeriod,
                               Duration bucketTtl, int maxKeys) {
        this(capacity, refillTokens, refillPeriod, bucketTtl, maxKeys, System::nanoTime);
    }

    /** Visible for tests, which inject a controllable clock instead of the real one. */
    InMemoryRateLimiter(int capacity, int refillTokens, Duration refillPeriod,
                        Duration bucketTtl, int maxKeys, LongSupplier nanoClock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be > 0");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be > 0");
        }
        if (bucketTtl == null || bucketTtl.isZero() || bucketTtl.isNegative()) {
            throw new IllegalArgumentException("bucketTtl must be > 0");
        }
        if (maxKeys <= 0) {
            throw new IllegalArgumentException("maxKeys must be > 0");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriod = refillPeriod;
        this.nanoClock = nanoClock;
        this.buckets = Caffeine.newBuilder()
                // The hard cap: however fast new keys arrive, the cache never holds more than this.
                .maximumSize(maxKeys)
                // Reclaim buckets for keys we have not seen for a while (idle cleanup).
                .expireAfterAccess(bucketTtl)
                .build();
    }

    /**
     * Consumes one token for {@code key}, creating that key's bucket if it does not exist yet.
     *
     * @return whether the request is allowed, plus the time to wait when it is not
     */
    public Decision tryConsume(String key) {
        long now = nanoClock.getAsLong();
        // Caffeine's get(key, fn) is atomic: concurrent callers share one bucket. An expired or
        // evicted entry is treated as absent, so the mapping function makes a fresh, full bucket.
        Bucket bucket = buckets.get(key, k -> new Bucket(capacity, now));
        return bucket.tryConsume(now, capacity, refillTokens, refillPeriod.toNanos());
    }

    /** Number of tracked keys right now; handy for diagnostics and tests. */
    int trackedKeys() {
        buckets.cleanUp();                 // force pending eviction before we count
        return (int) buckets.estimatedSize();
    }

    /**
     * One key's token state. Kept private and mutable; all mutation happens while holding the
     * bucket's monitor, so the arithmetic below never races.
     */
    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, long nowNanos) {
            this.tokens = capacity;                 // start full: allow the first burst
            this.lastRefillNanos = nowNanos;
        }

        synchronized Decision tryConsume(long nowNanos, int capacity,
                                         int refillTokens, long refillPeriodNanos) {
            // Refill proportionally to elapsed time, never exceeding the capacity.
            long elapsed = nowNanos - lastRefillNanos;
            if (elapsed > 0) {
                double added = (double) refillTokens * elapsed / refillPeriodNanos;
                tokens = Math.min(capacity, tokens + added);
                lastRefillNanos = nowNanos;
            }

            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new Decision(true, Duration.ZERO);
            }

            // No token: report how long until one is available, rounding UP so we never
            // tell the client to retry a hair too early.
            double tokensPerNano = (double) refillTokens / refillPeriodNanos;
            long waitNanos = (long) Math.ceil((1.0 - tokens) / tokensPerNano);
            return new Decision(false, Duration.ofNanos(waitNanos));
        }
    }
}
