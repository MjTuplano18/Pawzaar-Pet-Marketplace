package com.pawzaar.user;

// === jakarta.persistence = "how this class maps to a database table" ===
import jakarta.persistence.Column;        // describes one column
import jakarta.persistence.Entity;        // marks the class as a JPA entity
import jakarta.persistence.GeneratedValue; // auto-generate the id
import jakarta.persistence.GenerationType; // which id strategy (UUID)
import jakarta.persistence.Id;            // the primary key
import jakarta.persistence.PrePersist;    // run before INSERT
import jakarta.persistence.Table;         // the table name

// === lombok === (same entity rules as User/Pet: @Getter on the class, no @Data)
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token issued at login, mapped to the {@code refresh_tokens} table (migration V4).
 *
 * <p>WHY THIS EXISTS: access tokens are stateless JWTs and cannot be revoked before they expire.
 * A refresh token is the opposite by design - it is an opaque secret that lives in the database,
 * so it can be rotated on every use and revoked on logout.
 *
 * <p>SECURITY: only {@code SHA-256(rawToken)} is stored ({@code tokenHash}). If the database
 * leaks, the attacker cannot reconstruct a usable token from the hash. The raw token exists only
 * in the login/refresh response and in the client's storage.
 *
 * <p>Same style as {@link com.pawzaar.pet.Pet}: the owner is a raw {@code userId} UUID rather than
 * a JPA relationship, so this feature never triggers lazy-loading.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // 64 hex characters = SHA-256. UNIQUE in the database (see V4).
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * The only way to create one. Callers must pass an ALREADY-hashed token (the raw token still
     * lives in the service), exactly like {@link User#register} insists on an already-hashed
     * password.
     */
    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    /**
     * Marks the token unusable (rotation on refresh, or logout). We never delete the row: keeping
     * it means a replayed old token is recognised as revoked instead of looking like garbage.
     */
    public void revoke() {
        this.revoked = true;
    }

    /** Usable only if it is neither revoked nor past its expiry. */
    public boolean isActive(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
