package com.pawzaar.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the filter's routing logic: which requests it inspects, when it lets them
 * through, and that a denial is delegated to the exception resolver (never hand-written JSON).
 */
class RateLimitFilterTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final List<Exception> resolvedExceptions = new ArrayList<>();

    /** Stand-in for Spring MVC's resolver: records what it was asked to render. */
    private final HandlerExceptionResolver recordingResolver = (request, response, handler, ex) -> {
        resolvedExceptions.add(ex);
        response.setStatus(429);
        return new ModelAndView();
    };

    private RateLimitFilter filter(InMemoryRateLimiter limiter) {
        return new RateLimitFilter(limiter, List.of(LOGIN), false, recordingResolver);
    }

    private static InMemoryRateLimiter limiter(int capacity) {
        return new InMemoryRateLimiter(capacity, capacity, MINUTE, Duration.ofMinutes(10), 1000);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    @Test
    void letsRequestsThroughWhileTokensRemain() throws Exception {
        RateLimitFilter filter = filter(limiter(3));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", LOGIN), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "the chain should have continued");
        assertTrue(resolvedExceptions.isEmpty());
    }

    @Test
    void rejectsThroughTheResolverWhenTheBucketIsEmpty() throws Exception {
        RateLimitFilter filter = filter(limiter(1));

        MockFilterChain first = new MockFilterChain();
        filter.doFilter(request("POST", LOGIN), new MockHttpServletResponse(), first);
        assertNotNull(first.getRequest());

        MockFilterChain second = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("POST", LOGIN), response, second);

        assertNull(second.getRequest(), "a denied request must not reach the chain");
        assertEquals(429, response.getStatus());
        assertEquals(1, resolvedExceptions.size());
        assertInstanceOf(RateLimitExceededException.class, resolvedExceptions.get(0));
    }

    @Test
    void ignoresPathsThatAreNotProtected() throws Exception {
        RateLimitFilter filter = filter(limiter(1));
        // Drain the only token on the protected path.
        filter.doFilter(request("POST", LOGIN), new MockHttpServletResponse(), new MockFilterChain());

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/v1/pets"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "unprotected paths are never limited");
    }

    @Test
    void ignoresNonPostMethodsOnProtectedPaths() throws Exception {
        RateLimitFilter filter = filter(limiter(1));
        filter.doFilter(request("POST", LOGIN), new MockHttpServletResponse(), new MockFilterChain());

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("GET", LOGIN), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "only POST is limited");
    }

    @Test
    void bucketsAreKeyedByClientIp() throws Exception {
        RateLimitFilter filter = filter(limiter(1));

        MockHttpServletRequest first = request("POST", LOGIN);
        first.setRemoteAddr("10.0.0.1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = request("POST", LOGIN);
        second.setRemoteAddr("10.0.0.2");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(second, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "a different IP has its own bucket");
    }
}
