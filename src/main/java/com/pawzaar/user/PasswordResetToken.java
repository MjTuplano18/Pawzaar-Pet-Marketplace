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
 * A one-time password-reset token, mapped to {@code password_reset_tokens} (migration V13).
 *
 * <p>WHY THIS EXISTS: a user who forgot their password must prove they control the account's mailbox
 * before choosing a new one. Clicking a link that only reaches that mailbox is the standard proof,
 * exactly like email verification ({@link EmailVerificationToken}).
 *
 * <p>SECURITY: only {@code SHA-256(rawToken)} is stored ({@code tokenHash}), the token is single-use
 * and short-lived, and {@code GlobalExceptionHandler} renders an unknown/expired/spent token as a 400
 * with a deliberately generic message so token state cannot be probed. The TTL is shorter than a
 * verification token's because a reset link can take over an account.
 *
 * <p>Same style as the other entities: a raw {@code userId} UUID (no JPA relationship), a static
 * {@code issue(...)} factory, and no public constructor.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // 64 hex characters = SHA-256. UNIQUE in the database (see V13).
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
     * {@link EmailVerificationToken#issue} and {@link RefreshToken#issue}.
     */
    public static PasswordResetToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
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
