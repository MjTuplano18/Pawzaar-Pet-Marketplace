package com.pawzaar.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H10: the JWT setup fails fast on a weak secret and only accepts tokens minted with the expected
 * issuer.
 */
class JwtConfigTest {

    private static final String SECRET_256 = Base64.getEncoder().encodeToString(new byte[32]);

    private static JwtConfig configWith(String secret) {
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "secret", secret);
        ReflectionTestUtils.setField(config, "accessTokenValidityMinutes", 30L);
        ReflectionTestUtils.setField(config, "refreshTokenValidityDays", 7L);
        return config;
    }

    @Test
    void rejectsASecretShorterThan256Bits() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);   // 128 bits

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> configWith(tooShort).jwtSecretKey());

        assertTrue(ex.getMessage().contains("32"), "the error should say how many bytes are required");
    }

    @Test
    void acceptsA256BitSecret() {
        SecretKey key = configWith(SECRET_256).jwtSecretKey();
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    void decoderAcceptsATokenFromTheConfiguredIssuer() {
        JwtConfig config = configWith(SECRET_256);
        SecretKey key = config.jwtSecretKey();
        JwtDecoder decoder = config.jwtDecoder(key);

        Jwt jwt = decoder.decode(tokenFrom(config.jwtEncoder(key), JwtConfig.ISSUER));

        assertEquals(JwtConfig.ISSUER, jwt.getClaimAsString("iss"));
    }

    @Test
    void decoderRejectsATokenFromAnotherIssuer() {
        JwtConfig config = configWith(SECRET_256);
        SecretKey key = config.jwtSecretKey();
        JwtDecoder decoder = config.jwtDecoder(key);

        String foreign = tokenFrom(config.jwtEncoder(key), "some-other-issuer");

        assertThrows(JwtException.class, () -> decoder.decode(foreign));
    }

    /** Signs a minimal, otherwise-valid token with the given issuer. */
    private static String tokenFrom(JwtEncoder encoder, String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .subject("11111111-1111-1111-1111-111111111111")
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
