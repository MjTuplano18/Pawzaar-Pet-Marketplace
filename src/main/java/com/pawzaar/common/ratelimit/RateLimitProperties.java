package com.pawzaar.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Tunable settings for the rate limiter, bound from the {@code pawzaar.rate-limit.*} keys in
 * {@code application.yaml} (all overridable by environment variables on the host).
 *
 * <p>This is the 12-factor-config pattern the rest of the app uses: sensible defaults in code,
 * values injected from outside so a deployment can change behaviour without a rebuild.
 */
@ConfigurationProperties(prefix = "pawzaar.rate-limit")
public class RateLimitProperties {

    /** Master switch; set {@code RATE_LIMIT_ENABLED=false} to turn the whole filter off. */
    private boolean enabled = true;

    /** Maximum tokens a single client may have at once - i.e. the allowed burst. */
    private int capacity = 20;

    /** Tokens added back to every bucket each {@link #refillPeriod}. */
    private int refillTokens = 20;

    /** How often tokens refill (ISO-8601 or short form like {@code 1m}). */
    private Duration refillPeriod = Duration.ofMinutes(1);

    /** A bucket untouched for this long may be evicted once the map grows too large. */
    private Duration bucketTtl = Duration.ofMinutes(10);

    /** Upper bound on tracked client keys, to cap memory. */
    private int maxKeys = 10_000;

    /**
     * Whether to trust the {@code X-Forwarded-For} header for the client IP. Leave {@code false}
     * unless the app runs behind a proxy you control: the header is client-supplied and spoofable.
     * (A more robust alternative is {@code server.forward-headers-strategy: framework}.)
     */
    private boolean trustForwardedFor = false;

    /** The endpoints to protect. Defaults to the unauthenticated auth endpoints - the brute-force surface. */
    private List<String> paths = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            // M4d: a spent/idle bucket here also throttles verification-link guessing and re-sends.
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(int refillTokens) {
        this.refillTokens = refillTokens;
    }

    public Duration getRefillPeriod() {
        return refillPeriod;
    }

    public void setRefillPeriod(Duration refillPeriod) {
        this.refillPeriod = refillPeriod;
    }

    public Duration getBucketTtl() {
        return bucketTtl;
    }

    public void setBucketTtl(Duration bucketTtl) {
        this.bucketTtl = bucketTtl;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(int maxKeys) {
        this.maxKeys = maxKeys;
    }

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }
}
