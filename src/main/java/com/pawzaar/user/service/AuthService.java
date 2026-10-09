package com.pawzaar.user.service;

import com.pawzaar.user.EmailAlreadyRegisteredException;
import com.pawzaar.user.InvalidCredentialsException;
import com.pawzaar.user.User;
import com.pawzaar.user.dto.LoginRequest;
import com.pawzaar.user.dto.LoginResponse;
import com.pawzaar.user.dto.RegisterRequest;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

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

    // Constructor INJECTION: with a single constructor, Spring injects automatically -
    // no @Autowired needed. This also makes unit testing easy: you can pass fakes.
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       Duration accessTokenValidity) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenValidity = accessTokenValidity;
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
     * Verifies credentials and issues a JWT.
     *
     * <p>readOnly = true: login only READS the user row. The token is signed in memory with
     * the shared secret - no write, no database change.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
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

        return LoginResponse.bearer(issueToken(user), accessTokenValidity.toSeconds());
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
                user.getCreatedAt()
        );
    }

}
