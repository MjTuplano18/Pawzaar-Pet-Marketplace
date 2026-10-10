package com.pawzaar.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for outbound email, bound from {@code pawzaar.email.*} (overridable by environment
 * variables on the host), following the project's 12-factor-config pattern (M4d).
 */
@ConfigurationProperties(prefix = "pawzaar.email")
public class EmailProperties {

    /** How messages are delivered. See {@link Transport}; defaults to {@code LOG}. */
    private Transport transport = Transport.LOG;

    /** The "From" address shown to recipients. */
    private String from = "no-reply@pawzaar.local";

    /** How long a verification link stays valid before it must be re-sent. */
    private Duration verificationValidity = Duration.ofHours(24);

    /** The SPA route the verification link points at; the raw token is appended as {@code ?token=}. */
    private String verificationBaseUrl = "http://localhost:5173/verify-email";

    /** The subject line of the verification email. */
    private String verificationSubject = "Verify your Pawzaar email address";

    /**
     * Whether {@link LoggingEmailSender} prints the full message body. The body contains the
     * verification link (and therefore its token), so this is a development-only convenience.
     */
    private boolean logBody = false;

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public Duration getVerificationValidity() {
        return verificationValidity;
    }

    public void setVerificationValidity(Duration verificationValidity) {
        this.verificationValidity = verificationValidity;
    }

    public String getVerificationBaseUrl() {
        return verificationBaseUrl;
    }

    public void setVerificationBaseUrl(String verificationBaseUrl) {
        this.verificationBaseUrl = verificationBaseUrl;
    }

    public String getVerificationSubject() {
        return verificationSubject;
    }

    public void setVerificationSubject(String verificationSubject) {
        this.verificationSubject = verificationSubject;
    }

    public boolean isLogBody() {
        return logBody;
    }

    public void setLogBody(boolean logBody) {
        this.logBody = logBody;
    }

    /** Delivery mechanism for outbound email (M4d-2). */
    public enum Transport {
        /** Write the message to the application log. Dev/test default; needs no mail account. */
        LOG,
        /** Deliver over SMTP via the auto-configured {@code JavaMailSender}. */
        SMTP
    }
}
