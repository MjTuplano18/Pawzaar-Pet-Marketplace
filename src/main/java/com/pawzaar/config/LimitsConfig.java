package com.pawzaar.config;

import com.pawzaar.common.limits.ListingLimitsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the per-account marketplace limits ({@link ListingLimitsProperties}) from the
 * {@code pawzaar.limits.*} keys (H6). Kept separate from {@code StorageConfig} / {@code
 * RateLimitConfig}: this is neither about where bytes live nor about request throttling.
 */
@Configuration
@EnableConfigurationProperties(ListingLimitsProperties.class)
public class LimitsConfig {
}
