package com.pawzaar.user.controller;

import com.pawzaar.config.JwtConfig;
import com.pawzaar.config.SecurityConfig;
import com.pawzaar.user.Role;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for /api/v1/me: no database, the service is mocked, the security rules are the
 * real ones (imported). The subject claim of the test JWT must be a valid UUID, because the
 * controller converts it with UUID.fromString - that is the same contract the real tokens have.
 */
@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class ProfileControllerTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;   // stands in for the real service (which needs a DB)

    private static UserResponse profile() {
        return new UserResponse(USER_ID, "ana@pawzaar.test", "Ana Reyes", "+639171234567",
                Role.USER, true, Instant.parse("2026-10-01T00:00:00Z"), "Dog mom from Bulacan.");
    }

    @Test
    void meReturnsTheLoggedInUsersProfile() throws Exception {
        when(authService.getProfile(USER_ID)).thenReturn(profile());

        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(b -> b.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("ana@pawzaar.test"))
                .andExpect(jsonPath("$.displayName").value("Ana Reyes"))
                .andExpect(jsonPath("$.phone").value("+639171234567"))
                .andExpect(jsonPath("$.bio").value("Dog mom from Bulacan."))
                // The one field that must never be returned, even by accident.
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meWithoutATokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        // No token -> Spring Security rejects the request before the controller; the service
        // must never have been called.
        verifyNoInteractions(authService);
    }

    @Test
    void updateEditsTheProfile() throws Exception {
        UserResponse updated = new UserResponse(USER_ID, "ana@pawzaar.test", "Ana Reyes Edit",
                null, Role.USER, true, Instant.parse("2026-10-01T00:00:00Z"), "New bio");
        when(authService.updateProfile(any(), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/me")
                        .with(jwt().jwt(b -> b.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ana Reyes Edit\",\"phone\":\"\",\"bio\":\"New bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Reyes Edit"))
                .andExpect(jsonPath("$.bio").value("New bio"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void updateWithBlankDisplayNameYields400BeforeTheServiceRuns() throws Exception {
        mockMvc.perform(put("/api/v1/me")
                        .with(jwt().jwt(b -> b.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"   \",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        verifyNoInteractions(authService);
    }

    @Test
    void updateWithInvalidPhoneNumberYields400() throws Exception {
        mockMvc.perform(put("/api/v1/me")
                        .with(jwt().jwt(b -> b.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ana\",\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        verifyNoInteractions(authService);
    }
}