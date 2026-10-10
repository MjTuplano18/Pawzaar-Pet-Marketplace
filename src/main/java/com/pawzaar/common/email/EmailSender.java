package com.pawzaar.common.email;

/**
 * Sends a plain-text email. One method, so the rest of the app never knows <em>how</em> mail is
 * delivered (M4d).
 *
 * <p>This is the same seam as {@link com.pawzaar.common.image.ImageStorage}: business code asks for
 * a message to be sent, and a bean decides the transport. The default bean
 * ({@link LoggingEmailSender}) writes the message to the application log, which is enough for local
 * development and the test suite - no SMTP account or third-party API needed. A real deployment
 * replaces it with an SMTP/SES/SendGrid implementation; nothing else changes.
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
