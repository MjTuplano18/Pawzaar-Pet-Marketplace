package com.pawzaar.common.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M10: every request gets a correlation id, exposed in the {@code X-Request-Id} header and the MDC.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesASafeInboundRequestIdAndClearsTheMdcAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "checkout-123456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> duringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringRequest.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        // Same id echoed to the client and visible to every log line inside the request ...
        assertEquals("checkout-123456", response.getHeader(RequestIdFilter.HEADER));
        assertEquals("checkout-123456", duringRequest.get());
        // ... and gone once the request ends, so a pooled thread cannot mislabel the next one.
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void generatesAnIdWhenTheHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> { });

        String header = response.getHeader(RequestIdFilter.HEADER);
        assertTrue(header != null && !header.isBlank(), "a request id must always be returned");
    }

    @Test
    void ignoresAnUnsafeInboundIdAndGeneratesAFreshOne() throws Exception {
        // The inbound header is untrusted: newlines would be a log-injection vector.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "bad\r\ninjected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String header = response.getHeader(RequestIdFilter.HEADER);
        assertNotEquals("bad\r\ninjected", header);
        assertTrue(header.matches("[A-Za-z0-9._-]+"), "the generated id must be safe to log");
    }
}
