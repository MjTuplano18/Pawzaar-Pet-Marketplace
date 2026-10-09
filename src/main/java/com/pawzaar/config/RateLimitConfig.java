package com.pawzaar.config;

import com.pawzaar.common.ratelimit.InMemoryRateLimiter;
import com.pawzaar.common.ratelimit.RateLimitFilter;
import com.pawzaar.common.ratelimit.RateLimitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Wires the rate-limiter beans and registers the filter in the servlet chain.
 *
 * <p>Everything here is gated on {@code pawzaar.rate-limit.enabled} (default {@code true}), so a
 * test or an environment can switch the whole feature off with one property - the beans simply
 * are not created, and no filter runs.
 *
 * <p>The filter is registered as an ordinary servlet filter rather than inside
 * {@code SecurityConfig}. That keeps security configuration focused and, importantly, keeps the
 * {@code @WebMvcTest} slices (which import {@code SecurityConfig}) from needing to know about it.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(prefix = "pawzaar.rate-limit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public InMemoryRateLimiter inMemoryRateLimiter(RateLimitProperties properties) {
        return new InMemoryRateLimiter(
                properties.getCapacity(),
                properties.getRefillTokens(),
                properties.getRefillPeriod(),
                properties.getBucketTtl(),
                properties.getMaxKeys());
    }

    @Bean
    @ConditionalOnProperty(prefix = "pawzaar.rate-limit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            InMemoryRateLimiter limiter,
            RateLimitProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {

        RateLimitFilter filter = new RateLimitFilter(
                limiter, properties.getPaths(), properties.isTrustForwardedFor(), resolver);

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        // Spring Security's own filter chain runs at order -100. Placing us at -110 means a flood
        // is cut off as early as possible, without disturbing security or MVC.
        registration.setOrder(-110);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
