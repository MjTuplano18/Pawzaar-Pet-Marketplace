package com.pawzaar.user.repository;

import com.pawzaar.user.PasswordResetToken;
import com.pawzaar.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository slice test for the password-reset feature: the single-use claim and retire-all
 * statements must behave at the SQL level - a unit test with a mocked repository cannot prove the
 * conditional UPDATE. Mirrors {@link EmailVerificationTokenRepositoryTest}.
 *
 * <p>{@code replace = NONE}: runs against the real PostgreSQL in Docker, like the other DB slices.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User user() {
        return userRepository.saveAndFlush(User.register(
                "reset-" + UUID.randomUUID() + "@pawzaar.test",
                "{bcrypt}dev-only-hash", "Fixture User"));
    }

    private PasswordResetToken token(User owner, Instant expiresAt) {
        return passwordResetTokenRepository.saveAndFlush(
                PasswordResetToken.issue(owner.getId(), "hash-" + UUID.randomUUID(), expiresAt));
    }

    @Test
    void consumeIfActiveSucceedsOnceThenReportsNothingToDo() {
        PasswordResetToken token = token(user(), Instant.now().plusSeconds(3600));

        assertEquals(1,
                passwordResetTokenRepository.consumeIfActive(token.getTokenHash(), Instant.now()),
                "the first claim must win - the token is single-use");
        assertEquals(0,
                passwordResetTokenRepository.consumeIfActive(token.getTokenHash(), Instant.now()),
                "a second claim must find nothing active");
    }

    @Test
    void consumeIfActiveRefusesAnExpiredToken() {
        Instant past = Instant.now().minusSeconds(60);
        PasswordResetToken token = token(user(), past);

        assertEquals(0,
                passwordResetTokenRepository.consumeIfActive(token.getTokenHash(), Instant.now()),
                "an expired token must not be consumable");
    }

    @Test
    void invalidateAllForUserRetiresOnlyThatUsersUnusedTokens() {
        User owner = user();
        User other = user();
        PasswordResetToken first = token(owner, Instant.now().plusSeconds(3600));
        PasswordResetToken second = token(owner, Instant.now().plusSeconds(3600));
        PasswordResetToken untouched = token(other, Instant.now().plusSeconds(3600));

        assertEquals(2, passwordResetTokenRepository.invalidateAllForUser(owner.getId()),
                "both of the owner's outstanding tokens are retired");

        // The bulk UPDATE bypasses the persistence context, so clear it to read the fresh rows.
        entityManager.clear();
        assertTrue(passwordResetTokenRepository.findByTokenHash(first.getTokenHash())
                .orElseThrow().isUsed());
        assertTrue(passwordResetTokenRepository.findByTokenHash(second.getTokenHash())
                .orElseThrow().isUsed());
        assertFalse(passwordResetTokenRepository.findByTokenHash(untouched.getTokenHash())
                .orElseThrow().isUsed(), "another user's tokens must not be affected");
    }
}
