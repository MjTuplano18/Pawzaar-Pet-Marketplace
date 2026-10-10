package com.pawzaar.common.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * M4d-2: the SMTP sender turns our three-argument {@code send} into exactly one
 * {@link SimpleMailMessage} handed to Spring's {@code JavaMailSender}.
 */
class SmtpEmailSenderTest {

    @Test
    void sendsOnePlainTextMessageWithTheConfiguredFromAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailProperties properties = new EmailProperties();
        properties.setFrom("no-reply@pawzaar.test");

        new SmtpEmailSender(mailSender, properties)
                .send("ana@example.com", "Verify your Pawzaar email address", "Click: https://x/y?token=abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@pawzaar.test");
        assertThat(message.getTo()).containsExactly("ana@example.com");
        assertThat(message.getSubject()).isEqualTo("Verify your Pawzaar email address");
        assertThat(message.getText()).isEqualTo("Click: https://x/y?token=abc");
    }
}
