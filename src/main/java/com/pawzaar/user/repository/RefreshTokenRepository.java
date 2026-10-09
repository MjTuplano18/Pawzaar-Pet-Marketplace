package com.pawzaar.user.repository;

import com.pawzaar.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data layer for refresh tokens. Like every repository here, the SQL is generated from the
 * method names.
 *
 * <p>Lookups go through {@code tokenHash}, never the raw token: the raw value is never stored,
 * so a leak of this table reveals nothing usable.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
