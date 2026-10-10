package com.pawzaar.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id (M10).
 *
 * <p>What it does, once per request:
 * <ol>
 *   <li>reuses an inbound {@code X-Request-Id} when the caller sent a SAFE one, otherwise mints a
 *       UUID;</li>
 *   <li>puts it in the SLF4J {@link MDC} under {@code requestId}, so every log line written while
 *       handling the request carries it (see {@code logging.pattern.level} in application.yaml);</li>
 *   <li>echoes it back in the {@code X-Request-Id} response header, so a user can quote the exact
 *       id from a failed request and it can be grepped in the logs;</li>
 *   <li>clears the MDC afterwards - servlet threads are pooled, so a leaked value would mislabel
 *       the NEXT request.</li>
 * </ol>
 *
 * <p>The inbound header is untrusted input: it is length-capped and restricted to a safe character
 * set, which also prevents log-injection (embedded newlines/ANSI escapes) via the header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // runs before Spring Security, so even a rejected request is tagged
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Returns the header value if it is a safe, non-empty id; otherwise null (caller generates one). */
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        return SAFE_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
