package com.pawzaar.user.repository;

import com.pawzaar.user.RefreshToken;
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
 * Repository slice test for H3: the atomic rotation UPDATE and the revoke-all-family statement must
 * behave at the SQL level (a unit test with a mocked repository cannot prove the conditional UPDATE).
 *
 * <p>{@code replace = NONE}: runs against the real PostgreSQL in Docker, like the other DB slices.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User user() {
        return userRepository.saveAndFlush(User.register(
                "refresh-" + UUID.randomUUID() + "@pawzaar.test",
                "{bcrypt}dev-only-hash", "Fixture User"));
    }

    private RefreshToken token(User owner, String hash) {
        return refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(owner.getId(), hash, Instant.now().plusSeconds(3600)));
    }

    @Test
    void revokeIfActiveSucceedsOnceThenReportsNothingToDo() {
        RefreshToken token = token(user(), "hash-" + UUID.randomUUID());

        assertEquals(1, refreshTokenRepository.revokeIfActive(token.getTokenHash()),
                "the first claim must win");
        assertEquals(0, refreshTokenRepository.revokeIfActive(token.getTokenHash()),
                "a second claim must find nothing active - this is what makes rotation atomic");
    }

    @Test
    void revokeAllForUserOnlyTouchesThatUsersActiveTokens() {
        User owner = user();
        User other = user();
        RefreshToken first = token(owner, "a-" + UUID.randomUUID());
        RefreshToken second = token(owner, "b-" + UUID.randomUUID());
        RefreshToken untouched = token(other, "c-" + UUID.randomUUID());

        assertEquals(2, refreshTokenRepository.revokeAllForUser(owner.getId()),
                "both of the owner's active tokens are revoked");

        // The bulk UPDATE bypasses the persistence context, so clear it to read the fresh rows.
        entityManager.clear();
        assertTrue(refreshTokenRepository.findByTokenHash(first.getTokenHash()).orElseThrow().isRevoked());
        assertTrue(refreshTokenRepository.findByTokenHash(second.getTokenHash()).orElseThrow().isRevoked());
        assertFalse(refreshTokenRepository.findByTokenHash(untouched.getTokenHash()).orElseThrow().isRevoked(),
                "another user's tokens must not be affected");
    }

    @Test
    void revokeAllForUserLeavesAlreadyRevokedRowsAloneInTheCount() {
        User owner = user();
        RefreshToken token = token(owner, "d-" + UUID.randomUUID());
        refreshTokenRepository.revokeIfActive(token.getTokenHash());

        // The row is already revoked, so there is nothing left to change.
        assertEquals(0, refreshTokenRepository.revokeAllForUser(owner.getId()));
    }
}
