package com.beefit.api.infra.security.oauth2;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.SocialProvider;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.SocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Executa depois que o Spring valida o code exchange e a assinatura do id_token
 * do provedor (Google/Microsoft). A partir daqui o usuário já está autenticado
 * perante o provedor — resta resolver/criar a conta local e emitir nossa própria
 * sessão (JWT + refresh token).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String FALLBACK_DEVICE_ID = "oauth2-unknown-device";

    private final SocialAuthService socialAuthService;
    private final AuthService authService;
    private final SocialExchangeCodeStore exchangeCodeStore;
    private final OAuth2CallbackUrlResolver callbackUrlResolver;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        HttpSession session = request.getSession(false);
        String platform = readSessionAttribute(session, OAuth2PlatformAwareAuthorizationRequestResolver.PLATFORM_SESSION_ATTRIBUTE);

        try {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            SocialProvider provider = toProvider(oauthToken.getAuthorizedClientRegistrationId());

            // openid está no scope das duas registrations, então o principal e sempre
            // um OidcUser — usamos os getters tipados (sub/email/email_verified/name)
            // em vez de ler atributos crus por chave de string.
            OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();
            String providerUserId = oidcUser.getSubject();
            String email = oidcUser.getEmail();
            // A Microsoft (AAD) nem sempre preenche email_verified no id_token/userinfo;
            // quando ausente, tratamos como não verificado (nunca linka por e-mail "no escuro").
            boolean emailVerified = Boolean.TRUE.equals(oidcUser.getEmailVerified());
            String fullName = oidcUser.getFullName();

            User user = socialAuthService.resolveOrCreate(provider, providerUserId, email, emailVerified, fullName);

            String deviceId = readSessionAttribute(session, OAuth2PlatformAwareAuthorizationRequestResolver.DEVICE_ID_SESSION_ATTRIBUTE);
            LoginResponseDTO loginResponse = authService.loginSocialUser(user, deviceId != null ? deviceId : FALLBACK_DEVICE_ID);
            String code = exchangeCodeStore.issue(loginResponse);

            response.sendRedirect(callbackUrlResolver.buildRedirectUrl(platform, "code", code));
        } catch (RuntimeException e) {
            // Qualquer falha de regra de negocio (ex.: e-mail nao verificado colidindo
            // com uma conta existente) acontece aqui dentro de um filtro de seguranca,
            // antes do DispatcherServlet/GlobalExceptionHandler — deixar propagar
            // resultaria num 500 cru. Redireciona pro mesmo fluxo de erro do provedor.
            log.warn("Falha ao resolver/criar conta no login social: {}", e.getMessage(), e);
            response.sendRedirect(callbackUrlResolver.buildRedirectUrl(platform, "error", "social_login_failed"));
        }
    }

    private String readSessionAttribute(HttpSession session, String attribute) {
        return session != null ? (String) session.getAttribute(attribute) : null;
    }

    private SocialProvider toProvider(String registrationId) {
        return switch (registrationId) {
            case "google" -> SocialProvider.GOOGLE;
            case "microsoft" -> SocialProvider.MICROSOFT;
            default -> throw new IllegalStateException("Provedor social desconhecido: " + registrationId);
        };
    }
}
