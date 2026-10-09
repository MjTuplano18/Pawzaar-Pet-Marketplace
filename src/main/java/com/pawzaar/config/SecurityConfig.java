package com.pawzaar.config;

// Creates objects that Spring manages and makes available to the application.
import org.springframework.context.annotation.Bean;

// Marks this class as a Spring configuration class.
import org.springframework.context.annotation.Configuration;

// Provides methods for configuring HTTP security rules.
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

// Enables Spring Security for the web application.
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

// Defines how the application handles user sessions.
import org.springframework.security.config.http.SessionCreationPolicy;

// Represents the security filter chain that processes incoming HTTP requests.
import org.springframework.security.web.SecurityFilterChain;

// Reads the JWT's "role" claim and turns it into authorities (ROLE_USER / ROLE_SELLER).
// Injected into the filter chain below so authenticated requests carry real roles.
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import org.springframework.http.HttpMethod;


/**
 * Configures the security rules for the Pawzaar API.
 *
 * Spring Security checks incoming requests before they reach the controllers.
 * This class defines which endpoints are public and which require authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Creates the security filter chain used to protect API endpoints.
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        // Disables CSRF because the API is designed to use stateless token authentication.
        http.csrf(csrf -> csrf.disable());

        // Prevents Spring from storing authentication information in an HTTP session.
        // Each request will need to provide its authentication token.
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Defines which API endpoints can be accessed without authentication.
        // 3) Who is allowed to access what:
        http.authorizeHttpRequests(auth -> auth
                // Public: liveness check.
                .requestMatchers("/api/v1/health").permitAll()
                // MUST be public: when MVC raises an error (400 bad body, 405 wrong method,
                // 415 bad content-type...) it dispatches to /error. If security blocked that,
                // the real status would be replaced by a useless 403 with no body.
                .requestMatchers("/error").permitAll()
                // Public: BROWSING pets - but only HTTP GET (read-only).
                .requestMatchers(HttpMethod.GET, "/api/v1/pets", "/api/v1/pets/**").permitAll()
                // Auth must be PUBLIC: you cannot log in if logging in requires a token.
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh").permitAll()
                // Everything else (any method, any other URL): must be authenticated.
                .anyRequest().authenticated())

            // THE RESOURCE SERVER: Spring Security's JWT support, wired in one line.
            // On every protected request it now:
            //   1) looks for the "Authorization: Bearer <token>" header
            //   2) verifies the signature with our JwtDecoder (no database lookup!)
            //   3) converts the claims into authorities via the converter injected above
            //   4) answers a proper 401 + WWW-Authenticate when the token is missing,
            //      expired or forged - that is the 403 -> 401 change we predicted earlier
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        // Builds and returns the configured security filter chain.
        return http.build();
    }
}