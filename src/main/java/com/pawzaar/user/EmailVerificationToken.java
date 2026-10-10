package com.pawzaar.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A one-time email-verification token, mapped to {@code email_verification_tokens} (migration V12).
 *
 * <p>WHY THIS EXISTS (M4d): registration trusts whatever address the client typed. Clicking a link
 * that only reaches that mailbox is the standard way to prove the address is real. This row is the
 * server side of that link.
 *
 * <p>SECURITY: only {@code SHA-256(rawToken)} is stored ({@code tokenHash}), exactly like
 * {@link RefreshToken}. A token is single-use and expires; {@code GlobalExceptionHandler} renders an
 * unknown/expired/spent token as a 400, with a deliberately generic message so token state cannot be
 * probed.
 *
 * <p>Same style as the other entities: a raw {@code userId} UUID (no JPA relationship), a static
 * {@code issue(...)} factory, and no public constructor.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // 64 hex characters = SHA-256. UNIQUE in the database (see V12).
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * The only way to create one. Callers must pass an ALREADY-hashed token, exactly like
     * {@link RefreshToken#issue} and {@link User#register} insist on already-hashed secrets.
     */
    public static EmailVerificationToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    /** Still usable only if it was never spent and is not past its expiry. */
    public boolean isActive(Instant now) {
        return !used && expiresAt.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
