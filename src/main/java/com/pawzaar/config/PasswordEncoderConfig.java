package com.pawzaar.config;

// @Configuration = "this class holds @Bean methods"; Spring creates the class at startup.
import org.springframework.context.annotation.Configuration;

// @Bean = "the object returned by this METHOD is managed by Spring".
// Spring calls the method ONCE and keeps the result in its container.
import org.springframework.context.annotation.Bean;

// The INTERFACE we expose to the rest of the app. Injecting this instead of the class above
// means we can swap BCrypt for Argon2 later by editing only this file.
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;


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
        // Default strength 10 = 2^10 key-expansion rounds (~100ms). Higher = slower + safer.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
