package com.pawzaar.common.email;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * A real {@link EmailSender}: delivers plain-text mail over SMTP using Spring's
 * {@code JavaMailSender} (M4d-2). It is selected when {@code pawzaar.email.transport=smtp}; the
 * {@link LoggingEmailSender} stays the default everywhere else.
 *
 * <p>The class is deliberately provider-agnostic - it holds a {@code JavaMailSender}, so Mailtrap,
 * Gmail, Amazon SES and SendGrid all work by pointing {@code spring.mail.*} at the right host. No
 * provider SDK and no credentials in code.
 *
 * <p>{@code send} runs on the caller's thread and lets failures propagate: a registration whose
 * verification mail cannot be delivered should fail loudly (and roll back) rather than leave an
 * account whose owner will never receive a link.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.getFrom();
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        // Must match (or be allowed by) the authenticated SMTP mailbox, otherwise relays reject it.
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        // Plain text only - verification links are never rendered as HTML.
        message.setText(body);
        mailSender.send(message);
    }
}
