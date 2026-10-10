package com.pawzaar.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H7: Swagger UI and the OpenAPI document must NOT be reachable in production.
 *
 * <p>These endpoints describe every route, parameter and schema; leaving them public is a gift to
 * an attacker. The prod profile turns both off, so the routes are not even registered (404) rather
 * than being served (200) or merely secured (401) - there is nothing to enumerate.
 *
 * <p>This test boots the real context with the {@code prod} profile active, supplying the
 * environment placeholders the prod config demands (datasource, JWT secret, CORS origin). Storage
 * is forced to {@code local} for the test so no Supabase credentials are needed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "DATABASE_URL=jdbc:postgresql://localhost:5433/pawzaar",
                "DATABASE_USERNAME=pawzaar",
                "DATABASE_PASSWORD=pawzaar_dev",
                "JWT_SECRET=dGVzdC1vbmx5LXNlY3JldC1rZXktZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uLTAwMQ==",
                "CORS_ALLOWED_ORIGINS=http://localhost:5173",
                "pawzaar.storage.type=local",
                "pawzaar.storage.root=target/prod-test-uploads"
        })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdSwaggerDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentIsNotExposedInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsNotExposedInProd() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}
