package com.pawzaar.user.repository;

import com.pawzaar.user.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data layer for email-verification tokens (M4d). Like every repository here, the SQL is generated
 * from the method names; lookups go through {@code tokenHash}, never the raw token (never stored).
 *
 * <p>{@code consumeIfActive} is a single conditional UPDATE, mirroring
 * {@link RefreshTokenRepository#revokeIfActive}: it makes the token single-use even if two requests
 * present the same link at the same instant. {@code invalidateAllForUser} retires any outstanding
 * tokens when a fresh one is issued, so an older link cannot be replayed after a resend.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Atomically marks the token used if (and only if) it is still unused and not yet expired,
     * returning the number of rows changed: {@code 1} for the winner, {@code 0} for everyone else.
     */
    @Modifying
    @Query("update EmailVerificationToken t set t.used = true "
            + "where t.tokenHash = :tokenHash and t.used = false and t.expiresAt > :now")
    int consumeIfActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** Retires every still-unused token for a user (called when a new one is issued). */
    @Modifying
    @Query("update EmailVerificationToken t set t.used = true "
            + "where t.userId = :userId and t.used = false")
    int invalidateAllForUser(@Param("userId") UUID userId);
}
