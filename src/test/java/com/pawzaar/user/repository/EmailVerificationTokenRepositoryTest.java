package com.pawzaar.user.repository;

import com.pawzaar.user.EmailVerificationToken;
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
 * Repository slice test for M4d: the single-use claim and retire-all statements must behave at the
 * SQL level - a unit test with a mocked repository cannot prove the conditional UPDATE.
 *
 * <p>{@code replace = NONE}: runs against the real PostgreSQL in Docker, like the other DB slices.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailVerificationTokenRepositoryTest {

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User user() {
        return userRepository.saveAndFlush(User.register(
                "verify-" + UUID.randomUUID() + "@pawzaar.test",
                "{bcrypt}dev-only-hash", "Fixture User"));
    }

    private EmailVerificationToken token(User owner, Instant expiresAt) {
        return emailVerificationTokenRepository.saveAndFlush(
                EmailVerificationToken.issue(owner.getId(), "hash-" + UUID.randomUUID(), expiresAt));
    }

    @Test
    void consumeIfActiveSucceedsOnceThenReportsNothingToDo() {
        EmailVerificationToken token = token(user(), Instant.now().plusSeconds(3600));

        assertEquals(1,
                emailVerificationTokenRepository.consumeIfActive(token.getTokenHash(), Instant.now()),
                "the first claim must win - the token is single-use");
        assertEquals(0,
                emailVerificationTokenRepository.consumeIfActive(token.getTokenHash(), Instant.now()),
                "a second claim must find nothing active");
    }

    @Test
    void consumeIfActiveRefusesAnExpiredToken() {
        Instant past = Instant.now().minusSeconds(60);
        EmailVerificationToken token = token(user(), past);

        assertEquals(0,
                emailVerificationTokenRepository.consumeIfActive(token.getTokenHash(), Instant.now()),
                "an expired token must not be consumable");
    }

    @Test
    void invalidateAllForUserRetiresOnlyThatUsersUnusedTokens() {
        User owner = user();
        User other = user();
        EmailVerificationToken first = token(owner, Instant.now().plusSeconds(3600));
        EmailVerificationToken second = token(owner, Instant.now().plusSeconds(3600));
        EmailVerificationToken untouched = token(other, Instant.now().plusSeconds(3600));

        assertEquals(2, emailVerificationTokenRepository.invalidateAllForUser(owner.getId()),
                "both of the owner's outstanding tokens are retired");

        // The bulk UPDATE bypasses the persistence context, so clear it to read the fresh rows.
        entityManager.clear();
        assertTrue(emailVerificationTokenRepository.findByTokenHash(first.getTokenHash())
                .orElseThrow().isUsed());
        assertTrue(emailVerificationTokenRepository.findByTokenHash(second.getTokenHash())
                .orElseThrow().isUsed());
        assertFalse(emailVerificationTokenRepository.findByTokenHash(untouched.getTokenHash())
                .orElseThrow().isUsed(), "another user's tokens must not be affected");
    }
}
