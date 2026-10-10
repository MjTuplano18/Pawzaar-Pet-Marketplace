package com.pawzaar.user.service;

import com.pawzaar.user.EmailAlreadyRegisteredException;
import com.pawzaar.user.InvalidCredentialsException;
import com.pawzaar.user.InvalidRefreshTokenException;
import com.pawzaar.user.RefreshToken;
import com.pawzaar.user.User;
import com.pawzaar.user.UserNotFoundException;
import com.pawzaar.user.dto.LoginRequest;
import com.pawzaar.user.dto.RegisterRequest;
import com.pawzaar.user.dto.TokenResponse;
import com.pawzaar.user.dto.UpdateProfileRequest;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.repository.RefreshTokenRepository;
import com.pawzaar.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Business layer for the "auth" feature - the ONLY class allowed to talk to UserRepository.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>run database work inside a transaction;</li>
 *   <li>hold the business rules (email is unique, passwords are hashed, tokens are issued);</li>
 *   <li>translate entities into DTOs so {@code User} never leaves this layer.</li>
 * </ul>
 */
@Service
public class AuthService {

    // A REAL BCrypt hash that matches nothing, generated once at startup by the same encoder.
    // Used only for timing-attack protection in login(): it is never stored and never returned,
    // it just makes "unknown email" cost the same ~100ms as "wrong password".
    // (It MUST come from the encoder - a hand-written hash string would be missing the
    //  "{bcrypt}" prefix and make the encoder throw instead of returning false.)
    private final String dummyHash;

    // final = the dependency cannot be swapped after construction. private = only this class
    // may touch it. Spring fills both in through the constructor below.
    private final UserRepository userRepository;

    // The BCrypt bean from PasswordEncoderConfig.
    private final PasswordEncoder passwordEncoder;

    // Issues signed JWTs (the JwtEncoder bean from JwtConfig).
    private final JwtEncoder jwtEncoder;

    // How long a token stays valid (a Duration bean from JwtConfig).
    private final Duration accessTokenValidity;

    // Stores the SHA-256 hashes of issued refresh tokens (rotation + revocation live here).
    private final RefreshTokenRepository refreshTokenRepository;

    // How long a refresh token lives (a Duration bean from JwtConfig).
    private final Duration refreshTokenValidity;

    // One SecureRandom for the whole service: it is thread-safe and seeding it is expensive.
    private static final SecureRandom RANDOM = new SecureRandom();

    // Constructor INJECTION: with a single constructor, Spring injects automatically -
    // no @Autowired needed. This also makes unit testing easy: you can pass fakes.
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       Duration accessTokenValidity,
                       RefreshTokenRepository refreshTokenRepository,
                       Duration refreshTokenValidity) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenValidity = refreshTokenValidity;
        // One hash computed at startup (~100ms, once) - see the field comment.
        this.dummyHash = passwordEncoder.encode("pawzaar-dummy-password-for-timing-only");
    }

    // A WRITE transaction this time (no readOnly flag - it defaults to false, and it must,
    // because this method inserts a row). If anything below throws, the insert is rolled back.
    @Transactional
    public UserResponse register(RegisterRequest request) {

        // NORMALIZE the email: "Ana@Gmail.com" and "ana@gmail.com" are the SAME account.
        // Without this you get two users who can never log in consistently.
        // Locale.ROOT avoids the Turkish-i bug, where "I".toLowerCase() becomes a dotless "ı".
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Business rule #1: an email may only be used once.
        // The exception carries no HTTP knowledge - GlobalExceptionHandler turns it into 409.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        // Business rule #2: never store a raw password.
        // The plaintext exists only inside this method and is never written anywhere.
        // User.register(...) is the only way to build a User, and it insists on a hash.
        User user = User.register(
                email,
                passwordEncoder.encode(request.password()),   // <- HASHING happens exactly here
                request.displayName().trim()
        );

        // Optional field: only set it when the client actually sent one.
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }

        // save() = INSERT for a brand-new entity (it has no id yet);
        // it would be an UPDATE for an entity that already has one.
        User saved = userRepository.save(user);

        return toResponse(saved);   // entity -> DTO, exactly like PetService does
    }

    /**
     * Verifies credentials and issues a fresh access + refresh token pair.
     *
     * <p>This is a WRITE transaction: besides reading the user row and signing a JWT in memory,
     * it inserts a row into {@code refresh_tokens} (the hashed refresh token). If anything below
     * throws, that insert is rolled back.
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // orElse(null) is normally discouraged, but here it is deliberate: we must keep going
        // even when the account is unknown, so the response TIME cannot reveal whether it exists.
        User user = userRepository.findByEmail(email).orElse(null);

        // TIMING-ATTACK PROTECTION: BCrypt is intentionally slow, so we always run a comparison.
        // With a real hash when the user exists, and with DUMMY_HASH when it does not.
        // Without this, "unknown email" would answer instantly while "wrong password" took
        // ~100ms - a fingerprint attackers use to enumerate accounts.
        String hashToCompare = (user != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCompare);

        // ONE condition, ONE message: never tell the client which half was wrong.
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException(email);
        }

        return issueTokens(user);
    }

    /**
     * Exchanges a valid refresh token for a brand-new access + refresh token pair (rotation).
     *
     * <p>Rotation means the presented token is <b>burned</b> and a different one is returned, so a
     * stolen refresh token is only usable until the legitimate client next refreshes. The used row
     * is revoked rather than deleted, so a replayed token is detected instead of being ignored.
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired, or already revoked
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!stored.isActive(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(stored.getUserId())
                // The user could have been deleted since the token was issued.
                .orElseThrow(InvalidRefreshTokenException::new);

        stored.revoke();   // rotation: the old token can never be used again
        return issueTokens(user);
    }

    /**
     * Revokes a refresh token so it can no longer be exchanged. Idempotent: revoking an unknown
     * or already-revoked token is not an error, so a double-clicked logout still returns 204.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .ifPresent(RefreshToken::revoke);
    }

    // ── profile (GET/PUT /api/v1/me) ──────────────────────────────────────────

    /**
     * Returns the authenticated user's own profile. readOnly = true because this only reads.
     *
     * <p>The caller hands over the user id from the JWT's {@code sub} claim - the id is verified
     * by the token signature, so it is trustworthy with no body or header to spoof.
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return toResponse(findUser(userId));
    }

    /**
     * Replaces the authenticated user's editable profile fields: display name, phone, bio.
     *
     * <p>Everything NOT in {@link UpdateProfileRequest} stays untouched - email, role, verified
     * flag and the password hash have no setter for exactly this reason: a profile update can
     * never accidentally reassign an account.
     *
     * <p>{@code null} or {@code ""} for phone/bio means "clear this field"; {@code ""} phone is
     * also what validation accepts, so a client can remove a phone number by sending an empty one.
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setDisplayName(request.displayName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setBio(blankToNull(request.bio()));
        // save() on an entity that already has an id = UPDATE. It also returns the entity, so we
        // build the response from the freshly-saved instance.
        return toResponse(userRepository.save(user));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                // A token can outlive its account (user deleted). 404 beats a NullPointerException.
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /** A shared helper: null AND blank both mean "no value" for the optional profile fields. */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Signs a new access JWT and mints a new refresh token for this user.
     *
     * <p>The raw refresh token is returned to the caller ONCE and never stored - only its hash is
     * persisted (see {@link RefreshToken}).
     */
    private TokenResponse issueTokens(User user) {
        String accessToken = issueToken(user);

        String rawRefreshToken = generateRawToken();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                sha256Hex(rawRefreshToken),
                Instant.now().plus(refreshTokenValidity)));

        return TokenResponse.bearer(accessToken, rawRefreshToken, accessTokenValidity.toSeconds());
    }

    /** 256 bits from a cryptographically secure RNG, URL-safe Base64 (no padding). */
    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the raw token, as 64 lowercase hex characters - the only form we store. */
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated on every JVM; this branch is unreachable in practice.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Builds and signs a JWT for this user.
     *
     * <p>The payload (claims) is NOT encrypted - anyone can read it - so it may contain only
     * non-sensitive facts: who the user is and what they may do. Never a password, never a hash.
     */
    private String issueToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pawzaar")
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenValidity))
                .subject(user.getId().toString())        // "sub": the standard subject claim
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())    // read back by JwtAuthenticationConverter
                .claim("displayName", user.getDisplayName())
                .build();

        // Pin the algorithm in the header as well as the decoder (defence against alg confusion).
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Entity -> DTO. private static = only used here, and it needs no instance state.
    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRole(),
                user.isVerified(),   // Lombok's getter for a boolean field is "is..." not "get..."
                user.getCreatedAt(),
                user.getBio()
        );
    }

}
