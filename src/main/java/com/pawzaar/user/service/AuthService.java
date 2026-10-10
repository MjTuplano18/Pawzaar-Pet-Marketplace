package com.pawzaar.user.service;

// === avatar storage ===
import com.pawzaar.common.email.EmailProperties;
import com.pawzaar.common.email.EmailSender;
import com.pawzaar.common.image.ImageStorage;
import com.pawzaar.common.image.ImageStorageException;
import com.pawzaar.common.image.ImageValidator;
import com.pawzaar.common.image.ServedImage;
import com.pawzaar.common.image.StorageCleanup;
import com.pawzaar.common.image.ValidatedImage;

import com.pawzaar.user.AvatarNotFoundException;
import com.pawzaar.user.EmailAlreadyRegisteredException;
import com.pawzaar.user.EmailVerificationToken;
import com.pawzaar.user.InvalidCredentialsException;
import com.pawzaar.user.InvalidRefreshTokenException;
import com.pawzaar.user.InvalidResetTokenException;
import com.pawzaar.user.InvalidVerificationTokenException;
import com.pawzaar.user.PasswordResetToken;
import com.pawzaar.user.RefreshToken;
import com.pawzaar.user.User;
import com.pawzaar.user.UserNotFoundException;
import com.pawzaar.user.dto.LoginRequest;
import com.pawzaar.user.dto.RegisterRequest;
import com.pawzaar.user.dto.TokenResponse;
import com.pawzaar.user.dto.UpdateProfileRequest;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.repository.EmailVerificationTokenRepository;
import com.pawzaar.user.repository.PasswordResetTokenRepository;
import com.pawzaar.user.repository.RefreshTokenRepository;
import com.pawzaar.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
 *   <li>issue and consume single-use email-verification tokens (M4d);</li>
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

    // The avatar is stored in the PROFILE-picture bucket (pawzaar-user-profile). @Qualifier is
    // required here because THREE ImageStorage beans now exist; the pet feature takes the other one.
    private final ImageStorage profileImageStorage;

    // Same allowlist + magic-byte sniffing rules that protect pet images.
    private final ImageValidator imageValidator;

    // M4d: stores the SHA-256 hashes of email-verification tokens (single-use; see V12).
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    // Password reset: stores the SHA-256 hashes of reset tokens (single-use; see V13).
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    // M4d: delivers the verification link. The dev bean logs it; a real deployment swaps in SMTP.
    private final EmailSender emailSender;

    // M4d: from-address, link base URL and token lifetime for the verification email.
    private final EmailProperties emailProperties;

    // One SecureRandom for the whole service: it is thread-safe and seeding it is expensive.
    private static final SecureRandom RANDOM = new SecureRandom();

    // Constructor INJECTION: with a single constructor, Spring injects automatically -
    // no @Autowired needed. This also makes unit testing easy: you can pass fakes.
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       Duration accessTokenValidity,
                       RefreshTokenRepository refreshTokenRepository,
                       Duration refreshTokenValidity,
                       ImageValidator imageValidator,
                       @Qualifier("profileImageStorage") ImageStorage profileImageStorage,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailSender emailSender,
                       EmailProperties emailProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenValidity = refreshTokenValidity;
        this.imageValidator = imageValidator;
        this.profileImageStorage = profileImageStorage;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
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

        // M4d: prove the address is real. Issuing the token is part of this same transaction, so a
        // failure here rolls the whole registration back rather than leaving an account with no way
        // to verify. (Delivery itself happens through the pluggable EmailSender.)
        sendVerificationEmail(saved);

        return toResponse(saved);   // entity -> DTO, exactly like PetService does
    }

    // ── email verification (M4d) ──────────────────────────────────────────────

    /**
     * Consumes an emailed verification token and marks the account verified.
     *
     * <p>Single-use and atomic: {@link EmailVerificationTokenRepository#consumeIfActive} flips
     * {@code used} in one conditional UPDATE (checking expiry in the same statement), so two
     * requests racing on the same link cannot both succeed. Unknown, expired and already-spent
     * tokens all produce the same generic error, so token state cannot be probed.
     *
     * @throws InvalidVerificationTokenException if the token is unknown, expired, or already used
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = sha256Hex(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidVerificationTokenException::new);

        // Claim it atomically; 0 means it was already spent or has expired.
        if (emailVerificationTokenRepository.consumeIfActive(tokenHash, Instant.now()) == 0) {
            throw new InvalidVerificationTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                // The FK guarantees the user existed when the token was issued; a missing row means
                // the account was deleted since, which is indistinguishable from a bad token.
                .orElseThrow(InvalidVerificationTokenException::new);
        user.markVerified();
        userRepository.save(user);
    }

    /**
     * Re-sends a verification link to the caller. Idempotent when already verified: there is nothing
     * to prove, so no email is sent.
     *
     * <p>Issuing a fresh token retires any earlier outstanding one (see
     * {@link #sendVerificationEmail}), so only the newest link works.
     */
    @Transactional
    public void resendVerification(UUID userId) {
        User user = findUser(userId);
        if (user.isVerified()) {
            return;   // already verified - nothing to send
        }
        sendVerificationEmail(user);
    }

    /**
     * Issues a fresh verification token for the user and hands the link to the {@link EmailSender}.
     *
     * <p>Only the SHA-256 hash is persisted; the raw token exists just long enough to build the
     * link. Any previously issued token is retired first, so a resent link supersedes an older one.
     */
    private void sendVerificationEmail(User user) {
        emailVerificationTokenRepository.invalidateAllForUser(user.getId());

        String rawToken = generateRawToken();
        emailVerificationTokenRepository.save(EmailVerificationToken.issue(
                user.getId(),
                sha256Hex(rawToken),
                Instant.now().plus(emailProperties.getVerificationValidity())));

        String link = emailProperties.getVerificationBaseUrl() + "?token=" + rawToken;
        String body = "Welcome to Pawzaar!\n\n"
                + "Confirm your email address by opening this link:\n" + link + "\n\n"
                + "It expires in " + emailProperties.getVerificationValidity().toHours() + " hours. "
                + "If you did not create a Pawzaar account, you can ignore this message.";

        emailSender.send(user.getEmail(), emailProperties.getVerificationSubject(), body);
    }

    // ── password reset ─────────────────────────────────────────────────────────

    /**
     * Starts a password reset for the given address. ALWAYS succeeds, whether or not the email is
     * registered: revealing that an address has an account is enumeration, so the caller cannot tell
     * the two cases apart.
     *
     * <p>When the account does exist, any outstanding reset link is retired and a fresh single-use
     * token (stored only as its SHA-256 hash) is emailed.
     */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(email).ifPresent(this::sendPasswordResetEmail);
    }

    /**
     * Completes a password reset: consumes the emailed token and replaces the stored password hash.
     *
     * <p>Single-use and atomic - {@link PasswordResetTokenRepository#consumeIfActive} flips
     * {@code used} in one conditional UPDATE (checking expiry in the same statement), so two requests
     * racing on the same link cannot both succeed. Unknown, expired and already-spent tokens all
     * produce the same generic error, so token state cannot be probed.
     *
     * <p>A successful reset also revokes every refresh token for the account: if the reset was
     * triggered because the account was compromised, the attacker's session must die immediately.
     *
     * @throws InvalidResetTokenException if the token is unknown, expired, or already used
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256Hex(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidResetTokenException::new);

        // Claim it atomically; 0 means it was already spent or has expired.
        if (passwordResetTokenRepository.consumeIfActive(tokenHash, Instant.now()) == 0) {
            throw new InvalidResetTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                // The FK guarantees the user existed when the token was issued; a missing row means
                // the account was deleted since, which is indistinguishable from a bad token.
                .orElseThrow(InvalidResetTokenException::new);

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Cut every existing session, and retire any other outstanding reset link besides the one
        // just spent.
        refreshTokenRepository.revokeAllForUser(user.getId());
        passwordResetTokenRepository.invalidateAllForUser(user.getId());
    }

    /**
     * Issues a fresh reset token for the user and hands the link to the {@link EmailSender}.
     *
     * <p>Only the SHA-256 hash is persisted; the raw token exists just long enough to build the link.
     * Any previously issued token is retired first, so a newer link supersedes an older one.
     */
    private void sendPasswordResetEmail(User user) {
        passwordResetTokenRepository.invalidateAllForUser(user.getId());

        String rawToken = generateRawToken();
        passwordResetTokenRepository.save(PasswordResetToken.issue(
                user.getId(),
                sha256Hex(rawToken),
                Instant.now().plus(emailProperties.getResetValidity())));

        String link = emailProperties.getResetBaseUrl() + "?token=" + rawToken;
        String body = "Hi " + user.getDisplayName() + ",\n\n"
                + "We received a request to reset your Pawzaar password. Open this link to choose a new one:\n"
                + link + "\n\n"
                + "It expires in " + emailProperties.getResetValidity().toMinutes() + " minutes. "
                + "If you did not request this, you can safely ignore this email - your password will not change.";

        emailSender.send(user.getEmail(), emailProperties.getResetSubject(), body);
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
     * <p>H3: rotation is ATOMIC. The token is claimed with a single conditional UPDATE
     * ({@link RefreshTokenRepository#revokeIfActive}); only one concurrent refresh can win. And if the
     * presented token is already revoked, that is replay/leak detection - the user's entire token
     * family is revoked and they must log in again.
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired, already revoked, or lost
     *                                      a concurrent rotation race
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String tokenHash = sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        // REUSE DETECTION: a revoked token must never come back. It was rotated at an earlier refresh
        // or revoked at logout, so seeing it again means it leaked - an attacker replaying an old
        // token, or the real client having lost the newer one. Burn the whole family and force login.
        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(stored.getUserId());
            throw new InvalidRefreshTokenException();
        }

        if (!stored.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        // ATOMIC ROTATION: claim the token in one conditional UPDATE. The winner gets 1; a request
        // that raced another and lost gets 0, which is treated as reuse (the family is burned).
        if (refreshTokenRepository.revokeIfActive(tokenHash) == 0) {
            refreshTokenRepository.revokeAllForUser(stored.getUserId());
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(stored.getUserId())
                // The user could have been deleted since the token was issued.
                .orElseThrow(InvalidRefreshTokenException::new);

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

    // ── avatar (POST/GET/DELETE /api/v1/me/avatar) ─────────────────────────────

    /**
     * Sets - or REPLACES - the caller's avatar.
     *
     * <p>Order matters, and mirrors {@code PetImageService}: store the new file FIRST, then persist
     * the new key; if the database write fails, the just-stored file is deleted so it cannot become
     * an orphan. The OLD file is only removed after the row change has <b>committed</b> (H5) - doing
     * it any earlier would leave the account pointing at a file that no longer exists if the
     * transaction rolled back. A failed cleanup of the old file is tolerated: a stale object nobody
     * references anymore is harmless.
     */
    @Transactional
    public UserResponse setAvatar(UUID userId, MultipartFile file) {
        User user = findUser(userId);

        ValidatedImage validated = imageValidator.validate(file);
        String newKey = profileImageStorage.store(validated.data(), validated.extension());

        // H5: if this transaction rolls back (including a commit-time failure), the file just written
        // must not linger.
        StorageCleanup.afterRollback(() -> deleteQuietly(newKey));

        String oldKey = user.getAvatarStorageKey();
        try {
            user.setAvatarStorageKey(newKey);
            user.setAvatarContentType(validated.contentType());
            User saved = userRepository.save(user);
            // H5: the previous avatar's file is dead only once the row change COMMITS.
            StorageCleanup.afterCommit(() -> deleteQuietly(oldKey));
            return toResponse(saved);
        } catch (RuntimeException e) {
            // The database write failed: remove the file we just wrote so it does not linger.
            deleteQuietly(newKey);
            throw e;
        }
    }

    /**
     * Removes the avatar: clears the row's key/type and deletes the stored file. Idempotent, like
     * the other DELETE endpoints - removing an avatar the user never had is still a 204.
     *
     * <p>H5: the file is deleted only <b>after</b> the transaction commits, so a rollback leaves the
     * row and its file consistent.
     */
    @Transactional
    public void removeAvatar(UUID userId) {
        User user = findUser(userId);
        String key = user.getAvatarStorageKey();
        if (key == null) {
            return;                  // nothing to remove - and no exception, so DELETE stays 204
        }
        user.setAvatarStorageKey(null);
        user.setAvatarContentType(null);
        userRepository.save(user);
        StorageCleanup.afterCommit(() -> deleteQuietly(key));
    }

    /**
     * Reads the avatar's bytes plus the MIME type captured at upload time.
     *
     * @throws AvatarNotFoundException if the user has never set an avatar
     */
    @Transactional(readOnly = true)
    public ServedImage getAvatar(UUID userId) {
        User user = findUser(userId);
        if (user.getAvatarStorageKey() == null) {
            throw new AvatarNotFoundException(userId);
        }
        return new ServedImage(
                profileImageStorage.load(user.getAvatarStorageKey()),
                user.getAvatarContentType());
    }

    /**
     * Deletes a stored object but swallows storage failures: the key is no longer referenced by any
     * row, so a leftover object in the bucket is invisible and harmless (the API resolves the DB row
     * first). A hard failure here must not roll back the avatar change the user just made.
     */
    private void deleteQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            profileImageStorage.delete(storageKey);
        } catch (ImageStorageException ignored) {
            // deliberately ignored - see the javadoc above
        }
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
                .issuer(com.pawzaar.config.JwtConfig.ISSUER)   // single source of truth (H10)
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
                user.getBio(),
                avatarUrl(user)
        );
    }

    /**
     * The avatar's URL is DERIVED, never stored: if the user set one, it is always served from this
     * endpoint. Frontends can render {@code <img src={avatarUrl}>} directly, or show a placeholder
     * when it is {@code null}.
     */
    private static String avatarUrl(User user) {
        return user.getAvatarStorageKey() == null ? null : "/api/v1/me/avatar";
    }

}
