package com.beefit.api.infra.security;

import com.beefit.api.domain.AuthSession;
import com.beefit.api.domain.User;
import com.beefit.api.repository.AuthSessionRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
// A classe serve como a validação da request antes dela chegar no controller destino
public class SecurityFilter extends OncePerRequestFilter {

    public static final String AUTH_FAILURE_REASON_ATTRIBUTE = "beefit.auth.failure";
    public static final String VERIFIED_SESSION_ATTRIBUTE = "beefit.auth.verifiedSession";

    public record VerifiedSession(UUID id, Instant accessExpiresAt, LocalDateTime sessionExpiresAt) {}

    private static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/auth/social/exchange",
            "/error"
    };

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = resolveRequestPath(request);
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);

        if(token != null){
            var validation = tokenService.validateToken(token);

            if (validation.status() != TokenService.TokenValidationStatus.VALID) {
                request.setAttribute(AUTH_FAILURE_REASON_ATTRIBUTE, validation.status().name());
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails user = userRepository.findByEmail(validation.subject());

            if(user==null){
                request.setAttribute(AUTH_FAILURE_REASON_ATTRIBUTE, TokenService.TokenValidationStatus.INVALID.name());
                SecurityContextHolder.clearContext();

                filterChain.doFilter(request, response);
                return;
            }

            if (!user.isEnabled()) {
                request.setAttribute(AUTH_FAILURE_REASON_ATTRIBUTE, "BANNED");
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            // Sem esta checagem o JWT sozinho ja bastaria, e revogar a sessao no
            // logout ou na troca de senha nao teria efeito nenhum sobre o access
            // token ate ele expirar por conta propria.
            var session = findActiveSession(validation.sessionId(), user);
            if (session.isEmpty()) {
                request.setAttribute(AUTH_FAILURE_REASON_ATTRIBUTE, TokenService.TokenValidationStatus.INVALID.name());
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            request.setAttribute(VERIFIED_SESSION_ATTRIBUTE, new VerifiedSession(
                    validation.sessionId(), validation.expiresAt(), session.get().getExpiresAt()));
            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private Optional<AuthSession> findActiveSession(UUID sessionId, UserDetails authenticatedUser) {
        if (sessionId == null) {
            return Optional.empty();
        }

        return authSessionRepository.findById(sessionId)
                .filter(session -> session.getRevokedAt() == null)
                .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()))
                // O token nomeia o usuario pelo e-mail e a sessao pelo id; conferir
                // que os dois apontam para a mesma conta impede que um token
                // reaproveite a sessao de outra.
                .filter(session -> authenticatedUser instanceof User user
                        && session.getUser().getId().equals(user.getId()));
    }

    private String resolveRequestPath(HttpServletRequest request) {
        Object pathWithinHandlerMapping = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (pathWithinHandlerMapping instanceof String path && !path.isBlank()) {
            return path;
        }
        return request.getServletPath();
    }

    private String recoverToken(HttpServletRequest request){
        var authHeader = request.getHeader("Authorization");
        if(authHeader == null){
            return null;
        }
        return authHeader.replace("Bearer ","");
    }
}
