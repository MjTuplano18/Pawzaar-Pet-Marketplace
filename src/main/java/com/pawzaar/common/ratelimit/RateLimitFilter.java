package com.pawzaar.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

/**
 * A servlet filter that rejects abusive traffic to the authentication endpoints with HTTP 429.
 *
 * <p>It runs for every request but only acts on the paths listed in
 * {@link RateLimitProperties#getPaths()} and only on {@code POST} (the auth endpoints' verb), so
 * browser CORS preflights and normal browsing are untouched.
 *
 * <p>When a request is denied we do <b>not</b> hand-write JSON here. We hand a
 * {@link RateLimitExceededException} to Spring MVC's {@link HandlerExceptionResolver}, which runs
 * it through {@code GlobalExceptionHandler}. That keeps the "one error format" rule intact: a 429
 * looks exactly like every other {@code problem+json} response, with a {@code Retry-After} header.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final InMemoryRateLimiter limiter;
    private final List<String> protectedPaths;
    private final boolean trustForwardedFor;
    private final HandlerExceptionResolver resolver;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(InMemoryRateLimiter limiter,
                           List<String> protectedPaths,
                           boolean trustForwardedFor,
                           HandlerExceptionResolver resolver) {
        this.limiter = limiter;
        this.protectedPaths = List.copyOf(protectedPaths);
        this.trustForwardedFor = trustForwardedFor;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isProtected(request)) {
            InMemoryRateLimiter.Decision decision = limiter.tryConsume(clientKey(request));
            if (!decision.allowed()) {
                // Route through the shared advice: sets 429 + Retry-After + problem+json body.
                resolver.resolveException(request, response, null,
                        new RateLimitExceededException(decision.retryAfter()));
                // Return WITHOUT calling filterChain.doFilter: the request never reaches a controller.
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        // The auth endpoints are POST. Skipping other verbs means OPTIONS preflights and
        // accidental GETs never consume a client's tokens.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return protectedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * The bucket key: the client's IP address. Behind a proxy that terminates TLS, the socket IP is
     * the proxy's, so all clients would share one bucket - which is why the forwarded header is an
     * explicit, off-by-default opt-in (it is spoofable unless a trusted proxy strips it).
     */
    private String clientKey(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader(FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                // Left-most entry is the original client.
                return forwarded.split(",")[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
