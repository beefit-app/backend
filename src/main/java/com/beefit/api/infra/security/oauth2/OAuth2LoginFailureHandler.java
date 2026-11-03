package com.beefit.api.infra.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Cobre consentimento negado, code/state inválido e, mais importante, token do
 * provedor inválido/expirado (o Spring rejeita a troca do code ou a assinatura do
 * id_token antes de nosso código rodar, chegando aqui como OAuth2AuthenticationException).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2CallbackUrlResolver callbackUrlResolver;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("Falha no login social (provedor rejeitou ou token invalido/expirado): {}", exception.getMessage(), exception);
        HttpSession session = request.getSession(false);
        String platform = session != null
                ? (String) session.getAttribute(OAuth2PlatformAwareAuthorizationRequestResolver.PLATFORM_SESSION_ATTRIBUTE)
                : null;

        response.sendRedirect(callbackUrlResolver.buildRedirectUrl(platform, "error", "oauth_failed"));
    }
}
