package com.beefit.api.infra.security;

import com.beefit.api.domain.User;
import com.beefit.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component("securityChecker")
@RequiredArgsConstructor
// Esse security checker é uma classe para criar contextos que será combinados com o PreAuthorize no controller
// para fazer as verificações necessárias antes de executar uma ação
public class SecurityChecker {

    private final UserRepository userRepository;

    // @PreAuthorize("@securityChecker.isNotBanned") no controller
    public boolean isNotBanned() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return false;
        }

        // Verifica se o principal da autenticação é de fato um token JWT
        if (auth.getPrincipal() instanceof UserDetails userDetails) {
            // O próprio método isEnabled() do UserDetails serve para checar se está ativo/banido
            return userDetails.isEnabled();
        }

        return false;
    }
}