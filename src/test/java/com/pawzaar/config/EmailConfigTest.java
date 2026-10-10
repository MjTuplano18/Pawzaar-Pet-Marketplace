package com.pawzaar.config;

import com.pawzaar.common.email.EmailSender;
import com.pawzaar.common.email.LoggingEmailSender;
import com.pawzaar.common.email.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * M4d-2: the {@link EmailConfig} transport switch.
 *
 * <p>A plain {@link ApplicationContextRunner} (no full app, no database) is the cheapest way to
 * exercise a conditional bean. It proves the three behaviours that matter: log by default, SMTP when
 * asked for and available, and a loud failure when SMTP is asked for but nothing can deliver.
 */
class EmailConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EmailConfig.class);

    @Test
    void defaultsToTheLoggingSender() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context.getBean(EmailSender.class)).isInstanceOf(LoggingEmailSender.class);
        });
    }

    @Test
    void usesTheSmtpSenderWhenTransportIsSmtpAndAMailSenderExists() {
        runner.withPropertyValues("pawzaar.email.transport=smtp")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class);
                });
    }

    @Test
    void failsFastWhenTransportIsSmtpButNoMailSenderIsConfigured() {
        runner.withPropertyValues("pawzaar.email.transport=smtp")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }
}
