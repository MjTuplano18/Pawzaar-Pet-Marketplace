package com.pawzaar.config;

import com.pawzaar.common.email.EmailProperties;
import com.pawzaar.common.email.EmailSender;
import com.pawzaar.common.email.LoggingEmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires outbound email (M4d).
 *
 * <p>The {@link EmailSender} bean is a {@link LoggingEmailSender} <b>only if no other sender
 * exists</b>: a deployment that defines its own {@code EmailSender} bean (SMTP, SES, ...) silently
 * takes over, and this default backs off. That is what makes "log the link in dev, really send it
 * in production" a configuration change rather than a code change.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender emailSender(EmailProperties properties) {
        return new LoggingEmailSender(properties);
    }
}
