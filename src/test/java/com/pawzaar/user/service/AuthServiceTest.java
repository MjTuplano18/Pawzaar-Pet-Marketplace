package com.pawzaar.user.service;

import com.pawzaar.common.email.EmailProperties;
import com.pawzaar.common.email.EmailSender;
import com.pawzaar.common.image.ImageStorage;
import com.pawzaar.common.image.ImageValidator;
import com.pawzaar.common.image.ServedImage;
import com.pawzaar.common.image.ValidatedImage;
import com.pawzaar.config.PasswordEncoderConfig;
import com.pawzaar.user.AvatarNotFoundException;
import com.pawzaar.user.EmailVerificationToken;
import com.pawzaar.user.InvalidCredentialsException;
import com.pawzaar.user.InvalidRefreshTokenException;
import com.pawzaar.user.InvalidResetTokenException;
import com.pawzaar.user.InvalidVerificationTokenException;
import com.pawzaar.user.PasswordResetToken;
import com.pawzaar.user.RefreshToken;
import com.pawzaar.user.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the LOGIN and REFRESH rules. This class exists because a real bug slipped through
 * here: a hand-written "dummy" hash without the {bcrypt} prefix made the encoder throw, so login
 * answered 500 instead of 401 for unknown emails. These tests now guard that path.
 *
 * <p>Note the real BCryptPasswordEncoder (not a mock): the point is to test real hashing
 * behaviour, and it costs about 100ms per encode - irrelevant in a unit test.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "ana@pawzaar.test";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtEncoder jwtEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ImageValidator imageValidator;

    @Mock
    private ImageStorage profileImageStorage;

    // M4d
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private EmailSender emailSender;

    // Password reset
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    // Real properties object (plain class, no Spring needed) so the token lifetime/links are real.
    private final EmailProperties emailProperties = new EmailProperties();

    // The REAL production encoder bean (with the {bcrypt} prefix and the legacy fallback),
    // so these tests exercise the exact hashing configuration the app runs with.
    private final PasswordEncoder realEncoder = new PasswordEncoderConfig().passwordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                realEncoder,
                jwtEncoder,
                Duration.ofMinutes(30),
                refreshTokenRepository,
                Duration.ofDays(7),
                imageValidator,
                profileImageStorage,
                emailVerificationTokenRepository,
                passwordResetTokenRepository,
                emailSender,
                emailProperties);
    }

    private static User userWithHash(String hash) {
        try {
            var ctor = User.class.getDeclaredConstructor();     // protected constructor
            ctor.setAccessible(true);
            User user = ctor.newInstance();
            setField(user, "id", UUID.randomUUID());            // normally generated by Hibernate
            user.setEmail(EMAIL);
            user.setDisplayName("Ana Reyes");
            setField(user, "passwordHash", hash);               // no setter, by design
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(User user, String name, Object value) {
        try {
            var field = User.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(user, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubToken() {
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                // A built Jwt needs at least one claim, otherwise it throws
                // "IllegalArgument: claims cannot be empty".
                .thenReturn(Jwt.withTokenValue("a.b.c").header("alg", "HS256").claim("sub", EMAIL).build());
    }

    // ── login ──────────────────────────────────────────────────────────────────

    @Test
    void loginWithCorrectPasswordReturnsABearerTokenAndARefreshToken() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(userWithHash(realEncoder.encode("pawzaar123"))));
        stubToken();

        TokenResponse response = authService.login(new LoginRequest(EMAIL, "pawzaar123"));

        assertEquals("a.b.c", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(1800, response.expiresInSeconds());

        // Login must ALSO mint a refresh token and persist its hash (never the raw value).
        assertNotNull(response.refreshToken());
        assertFalse(response.refreshToken().isBlank());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginWithWrongPasswordFails() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(userWithHash(realEncoder.encode("correct-password"))));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest(EMAIL, "wrong-password")));
    }

    @Test
    void loginWithUnknownEmailFailsTheSameWayAndDoesNotCrash() {
        // The regression test: this used to throw IllegalArgumentException -> HTTP 500.
        when(userRepository.findByEmail("ghost@pawzaar.test")).thenReturn(Optional.empty());

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("ghost@pawzaar.test", "whatever123")));

        // Same message as a wrong password: no user enumeration.
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void loginAcceptsLegacyHashesStoredWithoutThePrefix() {
        // A hash written BEFORE the delegating encoder existed: plain BCrypt, no "{bcrypt}" prefix.
        String legacyHash = new BCryptPasswordEncoder().encode("pawzaar123");
        assertFalse(legacyHash.startsWith("{"), "this test needs an UNPREFIXED hash");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userWithHash(legacyHash)));
        stubToken();

        // Thanks to setDefaultPasswordEncoderForMatches(...) the login still succeeds instead of
        // throwing IllegalArgumentException (which used to surface as HTTP 500).
        TokenResponse response = authService.login(new LoginRequest(EMAIL, "pawzaar123"));

        assertEquals("a.b.c", response.accessToken());
    }

    @Test
    void loginNormalisesTheEmailBeforeLookingItUp() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("  ANA@Pawzaar.TEST  ", "whatever123")));

        // Proof: the repository was queried with the trimmed, lower-cased address.
        verify(userRepository).findByEmail(EMAIL);
    }

    // ── refresh ────────────────────────────────────────────────────────────────

    @Test
    void refreshRotatesTheTokenAndReturnsANewPair() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        RefreshToken stored = RefreshToken.issue(
                user.getId(), "irrelevant-hash", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        // H3: rotation now claims the token with a single conditional UPDATE - this request wins.
        when(refreshTokenRepository.revokeIfActive(anyString())).thenReturn(1);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubToken();

        TokenResponse response = authService.refresh("some-raw-refresh-token");

        assertEquals("a.b.c", response.accessToken());
        assertNotNull(response.refreshToken());

        // ROTATION: the presented token is burned, and a brand-new one is issued.
        verify(refreshTokenRepository).revokeIfActive(anyString());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshWithUnknownTokenFails() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh("never-issued"));
    }

    @Test
    void refreshWithRevokedTokenFailsAndBurnsTheWholeFamily() {
        // H3: replaying a token that was already rotated/revoked means it leaked. Every active token
        // for that user must be revoked, not just the one presented.
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.issue(userId, "h", Instant.now().plusSeconds(3600));
        stored.revoke();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh("already-used"));

        verify(refreshTokenRepository).revokeAllForUser(userId);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void refreshTreatsAConcurrentRotationLossAsReuse() {
        // H3: the conditional UPDATE changed 0 rows, so another request already rotated this token.
        // We must not issue a second pair - burn the family and 401.
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.issue(userId, "h", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfActive(anyString())).thenReturn(0);

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh("racing-token"));

        verify(refreshTokenRepository).revokeAllForUser(userId);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshWithExpiredTokenFails() {
        RefreshToken stored = RefreshToken.issue(UUID.randomUUID(), "h", Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh("expired"));
    }

    // ── logout ─────────────────────────────────────────────────────────────────

    @Test
    void logoutRevokesTheToken() {
        RefreshToken stored = RefreshToken.issue(UUID.randomUUID(), "h", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout("some-raw-refresh-token");

        assertTrue(stored.isRevoked());
    }

    @Test
    void logoutIsIdempotentForUnknownTokens() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Must NOT throw: logging out twice, or with a stale token, is still a success.
        authService.logout("never-issued");
    }

    // ── profile (GET/PUT /api/v1/me) ───────────────────────────────────────────

    @Test
    void getProfileReturnsTheUsersProfile() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        user.setPhone("+639171234567");
        user.setBio("Dog mom from Bulacan.");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = authService.getProfile(user.getId());

        assertEquals(user.getId(), response.id());
        assertEquals(EMAIL, response.email());
        assertEquals("Ana Reyes", response.displayName());
        assertEquals("+639171234567", response.phone());
        assertEquals("Dog mom from Bulacan.", response.bio());
    }

    @Test
    void getProfileOfAMissingUserFailsWithUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> authService.getProfile(userId));
    }

    @Test
    void updateProfileEditsOnlyTheEditableFields() {
        User user = userWithHash(realEncoder.encode("original-password"));
        user.setPhone("+639171234567");
        setField(user, "createdAt", Instant.parse("2026-10-01T00:00:00Z"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.updateProfile(user.getId(),
                new UpdateProfileRequest("  Ana Reyes Edit  ", "", "  New bio  "));

        // Editable fields replaced (trimmed); empty phone CLEARED to null.
        assertEquals("Ana Reyes Edit", response.displayName());
        assertNull(response.phone());
        assertEquals("New bio", response.bio());
        // Identity and credentials are frozen: email, role untouched, hash never touched.
        assertEquals(EMAIL, response.email());
        assertEquals(Role.USER, response.role());
        // The change was persisted (save = UPDATE for an entity that already has an id).
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileOfAMissingUserFailsWithUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> authService.updateProfile(userId, new UpdateProfileRequest("Ana", "", null)));
    }

    // ── avatar (POST/GET/DELETE /api/v1/me/avatar) ────────────────────────────

    @Test
    void setAvatarStoresPersistsAndReturnsTheDerivedUrl() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageValidator.validate(any()))
                .thenReturn(new ValidatedImage(new byte[]{1}, "png", "image/png"));
        when(profileImageStorage.store(any(), eq("png"))).thenReturn("avatar-key-1");

        UserResponse response = authService.setAvatar(user.getId(), anyPng());

        assertEquals("/api/v1/me/avatar", response.avatarUrl());
        assertEquals("avatar-key-1", user.getAvatarStorageKey());
        verify(profileImageStorage).store(any(), eq("png"));
        verify(profileImageStorage, never()).delete(any());   // no previous avatar to clean up
    }

    @Test
    void setAvatarReplacesThePreviousAvatarAndDeletesItsFile() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        user.setAvatarStorageKey("old-key");
        user.setAvatarContentType("image/png");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageValidator.validate(any()))
                .thenReturn(new ValidatedImage(new byte[]{1}, "png", "image/png"));
        when(profileImageStorage.store(any(), eq("png"))).thenReturn("new-key");

        authService.setAvatar(user.getId(), anyPng());

        assertEquals("new-key", user.getAvatarStorageKey());
        verify(profileImageStorage).delete("old-key");   // replaced, so the old file must go
    }

    @Test
    void removeAvatarClearsTheRowAndDeletesTheStoredFile() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        user.setAvatarStorageKey("old-key");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.removeAvatar(user.getId());

        assertNull(user.getAvatarStorageKey());
        assertNull(user.getAvatarContentType());
        verify(profileImageStorage).delete("old-key");
    }

    @Test
    void removeAvatarWithoutOneIsStillASuccess() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.removeAvatar(user.getId());   // must NOT throw -> DELETE stays 204

        verify(profileImageStorage, never()).delete(any());
    }

    @Test
    void getAvatarReturnsTheStoredBytesAndType() throws Exception {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        user.setAvatarStorageKey("key");
        user.setAvatarContentType("image/png");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileImageStorage.load("key")).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        ServedImage served = authService.getAvatar(user.getId());

        assertEquals("image/png", served.contentType());
        assertArrayEquals(new byte[]{1, 2, 3}, served.resource().getContentAsByteArray());
    }

    @Test
    void getAvatarWithoutOneFailsWithAvatarNotFound() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(AvatarNotFoundException.class, () -> authService.getAvatar(user.getId()));
    }

    // ── email verification (M4d) ──────────────────────────────────────────────

    @Test
    void registerIssuesAVerificationEmailStoringOnlyTheTokenHash() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        // The real repository assigns the generated id on insert; the mock must do the same, or the
        // verification token would be issued for a null user id.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            setField(user, "id", UUID.randomUUID());
            return user;
        });
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        authService.register(new RegisterRequest(EMAIL, "pawzaar123", "Ana Reyes", null));

        // A verification link was emailed...
        verify(emailSender).send(eq(EMAIL), anyString(), body.capture());
        String rawToken = tokenFromLink(body.getValue());

        // ...any earlier outstanding tokens were retired...
        verify(emailVerificationTokenRepository).invalidateAllForUser(any(UUID.class));

        // ...and what was persisted is the SHA-256 of the emailed token, never the token itself.
        ArgumentCaptor<EmailVerificationToken> stored =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(stored.capture());
        assertEquals(sha256Hex(rawToken), stored.getValue().getTokenHash());
        assertNotEquals(rawToken, stored.getValue().getTokenHash());
    }

    @Test
    void verifyEmailMarksTheUserVerified() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        EmailVerificationToken token = EmailVerificationToken.issue(
                user.getId(), "irrelevant-hash", Instant.now().plusSeconds(3600));
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(emailVerificationTokenRepository.consumeIfActive(anyString(), any(Instant.class))).thenReturn(1);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.verifyEmail("some-raw-token");

        assertTrue(user.isVerified());
        // The token is single-use: it is claimed by the atomic conditional UPDATE.
        verify(emailVerificationTokenRepository).consumeIfActive(anyString(), any(Instant.class));
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailRejectsAnUnknownToken() {
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidVerificationTokenException.class, () -> authService.verifyEmail("nope"));
    }

    @Test
    void verifyEmailRejectsATokenThatIsExpiredOrAlreadyUsed() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        EmailVerificationToken token = EmailVerificationToken.issue(
                user.getId(), "hash", Instant.now().plusSeconds(3600));
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        // 0 rows changed = it was already spent or has expired.
        when(emailVerificationTokenRepository.consumeIfActive(anyString(), any(Instant.class))).thenReturn(0);

        assertThrows(InvalidVerificationTokenException.class, () -> authService.verifyEmail("stale"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resendVerificationEmailsANewLinkWhenNotYetVerified() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.resendVerification(user.getId());

        verify(emailSender).send(eq(EMAIL), anyString(), anyString());
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void resendVerificationIsSilentWhenAlreadyVerified() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        user.markVerified();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.resendVerification(user.getId());

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(emailVerificationTokenRepository, never()).save(any(EmailVerificationToken.class));
    }

    // ── password reset ────────────────────────────────────────────────────────

    @Test
    void requestPasswordResetStoresOnlyTheTokenHashAndEmailsTheLink() {
        User user = userWithHash(realEncoder.encode("pawzaar123"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        authService.requestPasswordReset("  ANA@Pawzaar.TEST  ");

        // A reset link was emailed to the account address...
        verify(emailSender).send(eq(EMAIL), anyString(), body.capture());
        String rawToken = tokenFromLink(body.getValue());

        // ...any earlier outstanding link was retired...
        verify(passwordResetTokenRepository).invalidateAllForUser(user.getId());

        // ...and only the SHA-256 of the emailed token was persisted.
        ArgumentCaptor<PasswordResetToken> stored =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(stored.capture());
        assertEquals(sha256Hex(rawToken), stored.getValue().getTokenHash());
        assertNotEquals(rawToken, stored.getValue().getTokenHash());
    }

    @Test
    void requestPasswordResetForAnUnknownEmailIsSilent() {
        // Enumeration defence: the caller gets the same (empty) outcome either way, so an unknown
        // address must NOT trigger a send and must NOT throw.
        when(userRepository.findByEmail("ghost@pawzaar.test")).thenReturn(Optional.empty());

        authService.requestPasswordReset("ghost@pawzaar.test");

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPasswordChangesTheHashAndRevokesEverySession() {
        User user = userWithHash(realEncoder.encode("old-password"));
        PasswordResetToken token = PasswordResetToken.issue(
                user.getId(), "irrelevant-hash", Instant.now().plusSeconds(3600));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordResetTokenRepository.consumeIfActive(anyString(), any(Instant.class))).thenReturn(1);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword("some-raw-token", "new-pawzaar-password");

        // The stored hash is the NEW password (never the raw value)...
        assertTrue(realEncoder.matches("new-pawzaar-password", user.getPasswordHash()));
        assertFalse(realEncoder.matches("old-password", user.getPasswordHash()));
        verify(userRepository).save(user);

        // ...the token is single-use (claimed by the conditional UPDATE)...
        verify(passwordResetTokenRepository).consumeIfActive(anyString(), any(Instant.class));
        // ...every existing session is cut, and any other outstanding link retired.
        verify(refreshTokenRepository).revokeAllForUser(user.getId());
        verify(passwordResetTokenRepository).invalidateAllForUser(user.getId());
    }

    @Test
    void resetPasswordRejectsAnUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidResetTokenException.class,
                () -> authService.resetPassword("nope", "new-pawzaar-password"));

        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    @Test
    void resetPasswordRejectsATokenThatIsExpiredOrAlreadyUsed() {
        User user = userWithHash(realEncoder.encode("old-password"));
        PasswordResetToken token = PasswordResetToken.issue(
                user.getId(), "hash", Instant.now().plusSeconds(3600));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        // 0 rows changed = it was already spent or has expired.
        when(passwordResetTokenRepository.consumeIfActive(anyString(), any(Instant.class))).thenReturn(0);

        assertThrows(InvalidResetTokenException.class,
                () -> authService.resetPassword("stale", "new-pawzaar-password"));

        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    /** Pulls the raw token out of a body of the form "...?token=<value>\n\n...". */
    private static String tokenFromLink(String body) {
        String marker = "?token=";
        int start = body.indexOf(marker) + marker.length();
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return body.substring(start, end);
    }

    /** Independent SHA-256 so the test does not reuse the service's own helper. */
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A tiny stand-in upload; the validator is mocked, so its bytes are never examined. */
    private static MockMultipartFile anyPng() {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
    }
}
