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
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

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
                // Public: BROWSING pets - but only HTTP GET (read-only).
                .requestMatchers(HttpMethod.GET, "/api/v1/pets", "/api/v1/pets/**").permitAll()
                // Everything else (any method, any other URL): must be authenticated.
                .anyRequest().authenticated());

        // Builds and returns the configured security filter chain.
        return http.build();
    }
}