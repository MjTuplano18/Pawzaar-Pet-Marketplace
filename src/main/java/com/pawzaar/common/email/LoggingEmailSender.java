package com.pawzaar.common.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link EmailSender}: writes the message to the application log instead of delivering
 * it (M4d). This keeps local development and the whole test suite runnable with no SMTP account,
 * while still exercising the exact same call path a real sender would.
 *
 * <p>SECURITY: an email body can contain a secret (the verification link's token), so the body is
 * logged <b>only</b> when {@code pawzaar.email.log-body=true}. That flag is switched on in the dev
 * profile so you can copy the link, and left off everywhere else so tokens never reach the logs.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final EmailProperties properties;

    public LoggingEmailSender(EmailProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (properties.isLogBody()) {
            log.info("Email to {} | from: {} | subject: {}\n{}",
                    to, properties.getFrom(), subject, body);
        } else {
            log.info("Email to {} | from: {} | subject: {} "
                            + "(body suppressed; set pawzaar.email.log-body=true to print it)",
                    to, properties.getFrom(), subject);
        }
    }
}
