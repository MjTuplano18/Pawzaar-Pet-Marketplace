package com.pawzaar.user.controller;

import com.pawzaar.config.JwtConfig;
import com.pawzaar.config.SecurityConfig;
import com.pawzaar.user.InvalidCredentialsException;
import com.pawzaar.user.InvalidRefreshTokenException;
import com.pawzaar.user.dto.TokenResponse;
import com.pawzaar.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for the auth endpoints: no database, the service is mocked, the security
 * whitelist is the real one (imported), so we also prove the login/refresh/logout routes are public.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AuthControllerTest {

    private static final String VALID_BODY = """
            {"email":"ana@email.com","password":"pawzaar123"}
            """;

    private static final String REFRESH_BODY = """
            {"refreshToken":"opaque-refresh-value"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;   // stands in for the real service (which needs a DB)

    @Test
    void loginIsPublicAndReturnsAToken() throws Exception {
        when(authService.login(any())).thenReturn(
                TokenResponse.bearer("header.payload.signature", "refresh-token-value", 1800));

        // No Authorization header: this works ONLY because /auth/login is whitelisted.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("header.payload.signature"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800));
    }

    @Test
    void loginWithBadCredentialsYields401ProblemDetail() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("ana@email.com"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid credentials"))
                // The response must NOT reveal which half was wrong, nor echo the email back.
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void loginWithBlankFieldsYields400BeforeTheServiceRuns() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        // No stubbing on purpose: if the service were called, Mockito would fail the test.
        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    @Test
    void refreshIsPublicAndReturnsANewTokenPair() throws Exception {
        when(authService.refresh(any())).thenReturn(
                TokenResponse.bearer("new.access.token", "new-refresh-value", 1800));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-value"));
    }

    @Test
    void refreshWithBadTokenYields401ProblemDetail() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid refresh token"))
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid or expired"));
    }

    @Test
    void logoutIsPublicAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshWithBlankTokenYields400BeforeTheServiceRuns() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void registerLosingTheUniqueConstraintRaceYields409ProblemDetail() throws Exception {
        // Two registrations with the same email can both pass the service's pre-check; the loser
        // then hits the users.email unique constraint. That DataIntegrityViolationException must
        // become a 409 in the one error format, not a 500.
        when(authService.register(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@email.com","password":"pawzaar123","displayName":"Ana Reyes"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Data conflict"));
    }

    // ── M4d: email verification ────────────────────────────────────────────────

    @Test
    void verifyEmailIsPublicAndReturns204() throws Exception {
        // No Authorization header: this works ONLY because /auth/verify-email is whitelisted.
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"opaque-token-value\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void verifyEmailWithBlankTokenYields400BeforeTheServiceRuns() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void resendVerificationRequiresAuthentication() throws Exception {
        // /auth/verify-email/resend is deliberately NOT whitelisted: only the account owner may
        // trigger a send.
        mockMvc.perform(post("/api/v1/auth/verify-email/resend"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resendVerificationReturns204ForTheAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email/resend")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(authService)
                .resendVerification(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }
}
