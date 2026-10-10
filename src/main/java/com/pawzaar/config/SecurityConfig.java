package com.pawzaar.config;

// Creates objects that Spring manages and makes available to the application.
import org.springframework.context.annotation.Bean;

// Marks this class as a Spring configuration class.
import org.springframework.context.annotation.Configuration;

// Provides methods for configuring HTTP security rules.
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

// Customizer.withDefaults() reuses the default behaviour for a feature (here: CORS).
import org.springframework.security.config.Customizer;

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

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;


/**
 * Configures the security rules for the Pawzaar API.
 *
 * Spring Security checks incoming requests before they reach the controllers.
 * This class defines which endpoints are public and which require authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Creates the security filter chain used to protect API endpoints.
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        // Disables CSRF because the API is designed to use stateless token authentication.
        http.csrf(csrf -> csrf.disable());

        // CORS: lets a browser app on another origin (e.g. the React dev server on :5173) call
        // this API. The rules come from the CorsConfigurationSource bean in CorsConfig; this one
        // line tells Spring Security to consult it. Without it, the browser's preflight OPTIONS
        // request would hit the authentication filter and fail before MVC ever sees it.
        http.cors(Customizer.withDefaults());

        // Prevents Spring from storing authentication information in an HTTP session.
        // Each request will need to provide its authentication token.
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Defines which API endpoints can be accessed without authentication.
        // 3) Who is allowed to access what:
        http.authorizeHttpRequests(auth -> auth
                // Public: liveness check.
                .requestMatchers("/api/v1/health").permitAll()
                // Public: Actuator health endpoints, for the hosting platform's health check.
                // The app-wide rule is "everything must be authenticated", so health probes must
                // be whitelisted explicitly - otherwise the platform sees 401 and marks us down.
                // (Only health/info are exposed; see management.* in application.yaml.)
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // MUST be public: when MVC raises an error (400 bad body, 405 wrong method,
                // 415 bad content-type...) it dispatches to /error. If security blocked that,
                // the real status would be replaced by a useless 403 with no body.
                .requestMatchers("/error").permitAll()
                // Swagger UI and OpenAPI spec (springdoc-openapi).
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                 "/v3/api-docs/**", "/v3/api-docs").permitAll()
                // Public: BROWSING pets - but only HTTP GET (read-only).
                // POST/PUT/DELETE /api/v1/pets and GET /api/v1/me/pets fall through to anyRequest().authenticated().
                .requestMatchers(HttpMethod.GET, "/api/v1/pets", "/api/v1/pets/**").permitAll()
                // Auth must be PUBLIC: you cannot log in if logging in requires a token.
                // /auth/refresh and /auth/logout are public too, and deliberately so: they are
                // authenticated by the REFRESH token in the body, not by an access token. A client
                // whose access token just expired must still be able to reach /auth/refresh.
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        // M4d: the emailed token IS the credential, and the user may not be logged
                        // in yet. NOT the /resend sibling - that one stays authenticated.
                        "/api/v1/auth/verify-email").permitAll()
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