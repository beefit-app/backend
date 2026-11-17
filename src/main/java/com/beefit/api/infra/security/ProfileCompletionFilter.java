package com.beefit.api.infra.security;

import com.beefit.api.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Conta criada via login social nasce sem peso, meta semanal e aceite dos termos
 * (ver User.isProfileComplete). Enquanto o usuário não completa o cadastro
 * (POST /auth/complete-profile), nenhuma rota /api/** responde — sem isso, um
 * cliente que ignorasse a tela "Complete seu cadastro" poderia, por exemplo,
 * finalizar treinos com peso nulo ou usar o app sem ter aceitado os termos.
 *
 * Rotas /auth/** ficam livres (me, logout, complete-profile), assim como ler o
 * próprio perfil e excluir a conta — quem não quiser aceitar os termos precisa
 * conseguir sair do app levando os dados embora.
 *
 * Não é um @Component de propósito: só faz sentido dentro da cadeia do Spring
 * Security, depois do SecurityFilter ter montado a autenticação.
 */
public class ProfileCompletionFilter extends OncePerRequestFilter {

    private static final String OWN_PROFILE_PATH = "/api/profile/me";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        boolean ownProfile = OWN_PROFILE_PATH.equals(path);
        return ownProfile && (HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.DELETE.matches(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user
                && !user.isProfileComplete()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {
                        "status": 403,
                        "error": "PROFILE_INCOMPLETE",
                        "message": "Complete seu cadastro para continuar."
                    }
                    """);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
