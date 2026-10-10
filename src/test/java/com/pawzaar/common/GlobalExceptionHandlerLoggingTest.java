package com.pawzaar.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pawzaar.common.image.ImageStorageException;
import com.pawzaar.user.InvalidCredentialsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M10: failures that matter in production are logged - and the sensitive parts are not.
 *
 * <p>A logback {@link ListAppender} is attached to the handler's logger so the assertions can
 * inspect exactly what would have been written.
 */
class GlobalExceptionHandlerLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void storageFailureIsLoggedAtErrorWithTheCauseButNotLeakedToTheClient() {
        ProblemDetail problem = handler.handleImageStorage(
                new ImageStorageException("supabase key rejected"));

        assertEquals(502, problem.getStatus());
        assertTrue(hasLevel(Level.ERROR), "a storage failure must be logged at ERROR");
        // The internal cause never reaches the client.
        assertFalse(problem.getDetail().contains("supabase key rejected"));
    }

    @Test
    void unexpectedErrorIsLoggedWithItsStacktraceAndStaysGeneric() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("kaboom-detail"));

        assertEquals(500, response.getStatusCode().value());
        assertTrue(appender.list.stream()
                        .anyMatch(e -> e.getLevel() == Level.ERROR && e.getThrowableProxy() != null),
                "a 500 must be logged with the throwable so it can be diagnosed");
        assertFalse(response.getBody().getDetail().contains("kaboom-detail"));
    }

    @Test
    void invalidCredentialsAreLoggedWithoutAnySecret() {
        handler.handleInvalidCredentials(new InvalidCredentialsException("ana@email.com"));

        assertTrue(hasLevel(Level.WARN), "a failed login must be logged");
        // No email and certainly no password/token in any line.
        assertTrue(appender.list.stream()
                        .noneMatch(e -> e.getFormattedMessage().contains("ana@email.com")),
                "the attempted email must not be logged");
    }

    @Test
    void invalidRefreshTokenIsLoggedAsAWarning() {
        handler.handleInvalidRefreshToken(new com.pawzaar.user.InvalidRefreshTokenException());

        assertTrue(hasLevel(Level.WARN), "a rejected refresh token must be logged");
        // The token is a bearer credential and is never present in the log message.
        assertTrue(appender.list.stream()
                        .noneMatch(e -> e.getFormattedMessage().contains("token=") ),
                "the refresh token must never be logged");
    }

    private boolean hasLevel(Level level) {
        return appender.list.stream().anyMatch(e -> e.getLevel() == level);
    }
}
