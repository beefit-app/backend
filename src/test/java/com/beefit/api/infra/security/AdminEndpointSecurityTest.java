package com.beefit.api.infra.security;

import com.beefit.api.controller.AdminDashboardController;
import com.beefit.api.controller.AdminUserController;
import com.beefit.api.controller.AdminReportController;
import com.beefit.api.controller.AdminWorkoutController;
import com.beefit.api.controller.WorkoutMediaController;
import com.beefit.api.controller.AuthController;
import com.beefit.api.domain.AuthSession;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.mapper.UserResponseMapper;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.service.AdminDashboardService;
import com.beefit.api.service.AdminUserService;
import com.beefit.api.service.TokenService;
import com.beefit.api.service.ReportModerationService;
import com.beefit.api.service.WorkoutModerationService;
import com.beefit.api.service.WorkoutMediaService;
import com.beefit.api.service.RateLimitService;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.LegalConsentService;
import com.beefit.api.mapper.RegisterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(controllers = {
        AdminDashboardController.class,
        AdminUserController.class,
        AdminWorkoutController.class,
        AdminReportController.class,
        WorkoutMediaController.class,
        AuthController.class
})
@Import({SecurityConfiguration.class, SecurityFilter.class})
class AdminEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthSessionRepository authSessionRepository;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private WorkoutModerationService workoutModerationService;

    @MockitoBean
    private ReportModerationService reportModerationService;

    @MockitoBean
    private UserResponseMapper userResponseMapper;

    @MockitoBean
    private WorkoutMediaService workoutMediaService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LegalConsentService legalConsentService;

    @MockitoBean
    private RegisterMapper registerMapper;

    private User commonUser;
    private User bannedAdmin;

    @BeforeEach
    void setUp() {
        commonUser = user(UserRole.USER, null);
        bannedAdmin = user(UserRole.ADMIN, LocalDateTime.now().plusHours(1));
    }

    @Test
    void everyAdminEndpointRejectsCommonUsers() throws Exception {
        mockValidToken("common@example.com", commonUser);
        for (MockHttpServletRequestBuilder request : adminRequests()) {
            mockMvc.perform(request.header("Authorization", "Bearer common-token"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void authenticatedCommonUserGetsAccessDeniedContractInsteadOfAuthenticationFailure() throws Exception {
        mockValidToken("common@example.com", commonUser);
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer common-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void everyAdminEndpointRejectsInvalidTokens() throws Exception {
        when(tokenService.validateToken("invalid-token"))
                .thenReturn(new TokenService.TokenValidationResult(TokenService.TokenValidationStatus.INVALID, null, null));
        for (MockHttpServletRequestBuilder request : adminRequests()) {
            mockMvc.perform(request.header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void everyAdminEndpointRejectsBannedAdmins() throws Exception {
        mockValidToken("banned@example.com", bannedAdmin);
        for (MockHttpServletRequestBuilder request : adminRequests()) {
            mockMvc.perform(request.header("Authorization", "Bearer banned-token"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void mediaEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/media/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legalVersionsArePublicForRegistrationScreen() throws Exception {
        when(legalConsentService.termsVersion()).thenReturn("terms-v1");
        when(legalConsentService.privacyVersion()).thenReturn("privacy-v1");
        mockMvc.perform(get("/auth/legal-versions"))
                .andExpect(status().isOk());
    }

    @Test
    void apiResponsesIncludeSecurityHeaders() throws Exception {
        when(tokenService.validateToken("invalid-token"))
                .thenReturn(new TokenService.TokenValidationResult(TokenService.TokenValidationStatus.INVALID, null, null));

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    private void mockValidToken(String email, User user) {
        String token = user == commonUser ? "common-token" : "banned-token";
        UUID sessionId = UUID.randomUUID();
        when(tokenService.validateToken(token))
                .thenReturn(new TokenService.TokenValidationResult(
                        TokenService.TokenValidationStatus.VALID, email, sessionId));
        when(userRepository.findByEmail(email)).thenReturn(user);
        // O filtro so autentica se a sessao do token ainda estiver ativa.
        when(authSessionRepository.findById(sessionId)).thenReturn(java.util.Optional.of(activeSession(user)));
    }

    private AuthSession activeSession(User owner) {
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUser(owner);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        return session;
    }

    private List<MockHttpServletRequestBuilder> adminRequests() {
        UUID id = UUID.randomUUID();
        return List.of(
                get("/api/admin/dashboard"),
                get("/api/admin/users"),
                get("/api/admin/users/{id}", id),
                patch("/api/admin/users/{id}/ban", id),
                patch("/api/admin/users/{id}/unban", id),
                get("/api/admin/posts"),
                delete("/api/admin/posts/{id}", id),
                patch("/api/admin/posts/{id}", id),
                get("/api/admin/reports"));
    }

    private User user(UserRole role, LocalDateTime bannedUntil) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setUsername(role.name().toLowerCase());
        user.setRole(role);
        user.setBannedUntil(bannedUntil);
        return user;
    }
}
