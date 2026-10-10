package com.pawzaar.config;

import com.pawzaar.common.email.EmailProperties;
import com.pawzaar.common.email.EmailSender;
import com.pawzaar.common.email.LoggingEmailSender;
import com.pawzaar.common.email.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Wires outbound email (M4d / M4d-2).
 *
 * <p>Two implementations share the {@link EmailSender} seam:
 * <ul>
 *   <li>{@link LoggingEmailSender} (default) writes the message to the log - dev and tests need no
 *       account;</li>
 *   <li>{@link SmtpEmailSender} (M4d-2) really delivers mail when
 *       {@code pawzaar.email.transport=smtp} and a {@link JavaMailSender} is available.</li>
 * </ul>
 *
 * <p>The bean is created <b>only if no other {@code EmailSender} exists</b>: a deployment that
 * defines its own bean silently takes over and this default backs off. The transport switch keeps
 * "log the link in dev, really send it in production" a configuration change rather than a code
 * change.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender emailSender(EmailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        if (properties.getTransport() == EmailProperties.Transport.SMTP) {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                // Fail fast: transport=smtp with nothing to send with means verification mail would be
                // silently dropped. Refuse to start with an actionable message instead.
                throw new IllegalStateException(
                        "pawzaar.email.transport=smtp but no JavaMailSender bean is configured. "
                                + "Set spring.mail.host (env SPRING_MAIL_HOST) plus the other SPRING_MAIL_* "
                                + "settings, or set pawzaar.email.transport=log to disable real delivery.");
            }
            return new SmtpEmailSender(sender, properties);
        }
        return new LoggingEmailSender(properties);
    }
}
