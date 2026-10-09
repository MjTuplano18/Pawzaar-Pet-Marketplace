package com.pawzaar.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the auto-generated OpenAPI 3 spec produced by springdoc-openapi.
 *
 * <p>Swagger UI is available at {@code /swagger-ui.html} (no auth required).
 * The raw spec JSON is at {@code /v3/api-docs}.
 *
 * <p>The "bearerAuth" scheme tells the UI to show an "Authorize" button where testers
 * can paste a JWT and have it sent automatically on every protected request.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI pawzaarOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pawzaar API")
                        .description("Pet marketplace REST API")
                        .version("v1"))
                // Register the Bearer scheme once so every endpoint can reference it.
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                // Apply the scheme globally - public endpoints still work because Spring
                // Security decides access, not OpenAPI.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
