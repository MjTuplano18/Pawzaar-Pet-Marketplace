package com.pawzaar.config;

// @Configuration = "this class holds @Bean methods"; Spring creates the class at startup.
import org.springframework.context.annotation.Configuration;

// @Bean = "the object returned by this METHOD is managed by Spring".
// Spring calls the method ONCE and keeps the result in its container.
import org.springframework.context.annotation.Bean;

// The concrete BCrypt implementation (the actual hashing algorithm).
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// The INTERFACE we expose to the rest of the app. Injecting this instead of the class above
// means we can swap BCrypt for Argon2 later by editing only this file.
import org.springframework.security.crypto.password.PasswordEncoder;

// Writes "{bcrypt}..." prefixes, verifies any prefix it knows, and falls back for legacy hashes.
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;

import java.util.Map;  // maps an algorithm id ("bcrypt") to the encoder that handles it


/**
 * One password-hashing strategy for the whole application, available for injection anywhere.
 *
 * <p>What BCrypt gives us (the three reasons it exists):
 * <ul>
 *   <li><b>Salted</b> - the same password produces a different hash for every user, so
 *       precomputed "rainbow tables" of hashes are useless.</li>
 *   <li><b>Slow by design</b> - ~100ms per hash. Awkward for a legitimate login (once),
 *       painful for an attacker trying billions of guesses.</li>
 *   <li><b>One-way</b> - we cannot recover the password, only check a guess against the hash.</li>
 * </ul>
 *
 * <p>Spring Boot would auto-configure BCrypt if you declared a PasswordEncoder bean anywhere.
 * This class makes that choice EXPLICIT instead of magic - one file to read when someone
 * asks "how do we hash passwords here?".
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();   // strength 10 (~100ms per hash)

        // New hashes are stored WITH an algorithm prefix: "{bcrypt}$2a$10$...".
        // That prefix is what makes a future migration to Argon2 possible without breaking
        // existing passwords.
        Map<String, PasswordEncoder> encodersById = Map.of("bcrypt", bcrypt);

        // Spring Security 7 has no 3-argument constructor any more; the fallback for legacy
        // hashes is set with a setter. Without it, logging in as a user whose hash was written
        // BEFORE the delegating encoder existed throws IllegalArgumentException
        // (a 500 where the client should get a 401).
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", encodersById);
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }
}
