package com.pawzaar.config;

// Spring's DI annotations (core Spring, not security-specific).
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The JWT model: claims, headers, encoder/decoder interfaces.
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

// Maps JWT claims -> Spring Security "authorities" (ROLE_USER / ROLE_SELLER).
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

// Nimbus JOSE library (bundled with spring-security-oauth2-jose).
import com.nimbusds.jose.jwk.source.ImmutableSecret;

// JDK crypto (javax.crypto is part of the JDK - NOT the old javax.* namespace problem).
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.time.Duration;
import java.util.Base64;

/**
 * JWT issuing and verification, built on Spring Security's own support
 * (spring-boot-starter-oauth2-resource-server) instead of hand-rolled filters.
 *
 * <p>WHAT IS A JWT? Three Base64url parts joined by dots:
 * <pre>
 *   eyJhbGci...  .  eyJzdWIiOi...  .  SflKxwRJSM...
 *   header         payload (claims)   signature (HMAC-SHA256 of the first two)
 * </pre>
 * The server can VERIFY the signature with its secret - no database lookup needed, which is
 * what "stateless" means. The payload is NOT encrypted: never put secrets in it.
 *
 * <p>We use HMAC-SHA256 (symmetric): the same secret signs and verifies. That suits a single
 * service that both issues and checks its own tokens. (When several independent services must
 * verify tokens, you'd switch to asymmetric RSA keys so only the issuer holds the private key.)
 */
@Configuration
public class JwtConfig {

    // Injected from application.yaml -> pawzaar.jwt.secret (which reads the JWT_SECRET env var).
    @Value("${pawzaar.jwt.secret}")
    private String secret;

    @Value("${pawzaar.jwt.access-token-validity-minutes:30}")
    private long accessTokenValidityMinutes;

    /**
     * Turns the Base64 string into the raw HMAC key bytes.
     * HS256 requires at least 256 bits (32 bytes) - a short key is silently rejected by the
     * library, which is exactly the protection you want.
     */
    @Bean
    public SecretKey jwtSecretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /** Issues tokens (used by AuthService when someone logs in). */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /**
     * Verifies tokens on every protected request (used by Spring Security automatically).
     * It checks: signature valid, algorithm is HS256 (never trust the header blindly), and
     * exp/iat timestamps make sense.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)   // pin the algorithm: blocks "alg: none" attacks
                .build();
    }

    /**
     * Turns our "role" claim into Spring authorities. Without this, every authenticated user
     * ends up with the default SCOPE_ authorities and has-authority(...) checks never pass.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");   // our custom claim
        authorities.setAuthorityPrefix("ROLE_");      // "SELLER" -> "ROLE_SELLER"

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        // Use the user id as the authentication name, so handlers can read the current user.
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    /** How long the tokens we issue stay valid. Exposed as a bean so the service can take it
     *  through the constructor instead of reading configuration itself. */
    @Bean
    public Duration accessTokenValidity() {
        return Duration.ofMinutes(accessTokenValidityMinutes);
    }
}
