package com.beefit.api.infra.security;

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

@Component
@RequiredArgsConstructor
// A classe serve como a validação da request antes dela chegar no controller destino
public class SecurityFilter extends OncePerRequestFilter {

    public static final String AUTH_FAILURE_REASON_ATTRIBUTE = "beefit.auth.failure";

    private static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout"
    };

    private final TokenService tokenService;
    private final UserRepository userRepository;

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

            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
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
