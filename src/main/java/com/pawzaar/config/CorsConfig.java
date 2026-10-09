package com.pawzaar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cross-Origin Resource Sharing (CORS) rules for browser clients.
 *
 * <p>WHY THIS EXISTS: a browser blocks JavaScript on {@code http://localhost:5173} from reading
 * responses from {@code http://localhost:8080} unless the API opts in via CORS headers ("same-origin
 * policy"). "It works in Postman" proves nothing - Postman is not a browser and ignores CORS.
 *
 * <p>WHY AN ALLOWLIST, NOT {@code "*"}: {@code "*"} lets ANY website call the API with the user's
 * browser. A concrete list (from configuration) means only our own front ends are trusted.
 *
 * <p>WHY NOT allowCredentials: we authenticate with an {@code Authorization: Bearer} header, which
 * the front end adds explicitly - not with cookies the browser attaches automatically. Keeping
 * credentials OFF removes the entire class of CSRF attacks.
 *
 * <p>The bean is picked up by SecurityConfig's {@code http.cors(...)}, which makes Spring Security
 * answer the preflight {@code OPTIONS} request itself instead of rejecting it as unauthenticated.
 */
@Configuration
public class CorsConfig {

    // Injected as a List<String>: Spring splits the comma-separated value for us.
    // Example: pawzaar.web.cors.allowed-origins=http://localhost:5173,https://pawzaar.ph
    @Value("${pawzaar.web.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Which front-end origins may call us. Exact origins only (scheme + host + port).
        config.setAllowedOrigins(allowedOrigins);

        // Which HTTP verbs the browser may use cross-origin. OPTIONS is the preflight itself.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Which request headers the browser may send. Authorization carries the JWT.
        // A wildcard here would be *almost* fine, but naming them keeps the surface small.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // Let the browser cache the preflight result for an hour instead of re-asking each call.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // One rule for the whole API - there is no per-endpoint CORS difference.
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
