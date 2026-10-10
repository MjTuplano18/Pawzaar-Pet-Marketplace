package com.pawzaar.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M14: springdoc-openapi must be compatible with the Spring Boot line in use. 2.x targets Boot 3 /
 * Spring Framework 6; this project is on Boot 4 / Framework 7, so it uses springdoc 3.x.
 *
 * <p>This boots the real context with Swagger ENABLED (the dev/test default) and asserts the OpenAPI
 * document is actually generated — the counterpart to {@link ProdSwaggerDisabledTest}, which proves
 * both routes are switched off in production. Without this, a springdoc/Boot version mismatch would
 * only surface at runtime, not in the build.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentIsGenerated() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                // The title is set by OpenApiConfig.pawzaarOpenAPI(), so this also proves our
                // bean is the one feeding springdoc.
                .andExpect(jsonPath("$.info.title").value("Pawzaar API"));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        // Confirms the springdoc UI webjar is wired up and reachable in non-prod.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
