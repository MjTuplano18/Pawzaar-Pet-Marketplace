package com.pawzaar.user.repository;

import com.pawzaar.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Data layer for refresh tokens. Like every repository here, the SQL is generated from the
 * method names.
 *
 * <p>Lookups go through {@code tokenHash}, never the raw token: the raw value is never stored,
 * so a leak of this table reveals nothing usable.
 *
 * <p>The two {@code @Modifying} statements exist for H3 - rotation and reuse detection must be a
 * single conditional UPDATE, not a read-then-write, so concurrent refreshes cannot both win.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomically revokes the token if (and only if) it is still active, returning the number of rows
     * changed: {@code 1} for the winner, {@code 0} for everyone else.
     *
     * <p>This is the whole point of H3. The old flow read the row, checked {@code isActive}, then
     * wrote {@code revoked = true} - a classic check-then-act race. Two requests replaying the same
     * token could both read "active" before either write commits and both be issued new tokens. A
     * conditional UPDATE moves the check and the write into one statement the database serialises.
     */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true "
            + "where t.tokenHash = :tokenHash and t.revoked = false")
    int revokeIfActive(@Param("tokenHash") String tokenHash);

    /**
     * Revokes every still-active token for a user. Called when a revoked token is replayed (reuse
     * detection): the token family is treated as compromised, so all sessions of that account are
     * cut and the user must log in again.
     */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true "
            + "where t.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId);
}
