package com.beefit.api.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.beefit.api.domain.AuthSession;
import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.service.TokenService;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    private static final String TOKEN = "token-de-teste";
    private static final String EMAIL = "alguem@example.com";

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @InjectMocks
    private SecurityFilter securityFilter;

    private User user;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setRole(UserRole.USER);
        sessionId = UUID.randomUUID();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AuthSession session(User owner, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        AuthSession session = new AuthSession();
        session.setId(sessionId);
        session.setUser(owner);
        session.setExpiresAt(expiresAt);
        session.setRevokedAt(revokedAt);
        return session;
    }

    private MockHttpServletRequest executeFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile/me");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        securityFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return request;
    }

    private void tokenPointsToSession() {
        when(tokenService.validateToken(TOKEN)).thenReturn(
                new TokenService.TokenValidationResult(TokenService.TokenValidationStatus.VALID, EMAIL, sessionId));
        when(userRepository.findByEmail(EMAIL)).thenReturn(user);
    }

    @Test
    void authenticatesWhenSessionIsActive() throws Exception {
        tokenPointsToSession();
        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(user, LocalDateTime.now().plusDays(1), null)));

        executeFilter();

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    // O caso que motivou a mudanca: apos o logout a sessao fica revogada, e o
    // access token emitido para ela nao pode mais valer.
    @Test
    void rejectsTokenOfRevokedSession() throws Exception {
        tokenPointsToSession();
        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(user, LocalDateTime.now().plusDays(1), LocalDateTime.now())));

        MockHttpServletRequest request = executeFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(TokenService.TokenValidationStatus.INVALID.name(),
                request.getAttribute(SecurityFilter.AUTH_FAILURE_REASON_ATTRIBUTE));
    }

    @Test
    void rejectsTokenOfExpiredSession() throws Exception {
        tokenPointsToSession();
        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(user, LocalDateTime.now().minusMinutes(1), null)));

        executeFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsTokenWhoseSessionNoLongerExists() throws Exception {
        tokenPointsToSession();
        when(authSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        executeFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // Assinatura valida e sessao viva nao bastam: a sessao precisa ser da conta
    // que o token nomeia, senao um token reaproveitaria a sessao de outra pessoa.
    @Test
    void rejectsSessionBelongingToAnotherUser() throws Exception {
        User outro = new User();
        outro.setId(UUID.randomUUID());
        outro.setEmail("outro@example.com");
        outro.setRole(UserRole.USER);

        tokenPointsToSession();
        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(outro, LocalDateTime.now().plusDays(1), null)));

        executeFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
