package com.beefit.api.infra.security.oauth2;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.SocialProvider;
import com.beefit.api.dto.auth.LoginResponseDTO;
import com.beefit.api.exception.user.RegistrationClosedException;
import com.beefit.api.exception.user.RegistrationRestrictedException;
import com.beefit.api.service.AuthService;
import com.beefit.api.service.SocialAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 * do provedor (Google/Microsoft/Apple). A partir daqui o usuário já está autenticado
 * perante o provedor — resta resolver/criar a conta local e emitir nossa própria
 * sessão (JWT + refresh token).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String FALLBACK_DEVICE_ID = "oauth2-unknown-device";

    // Instanciado direto em vez de injetado: o bean ObjectMapper autoconfigurado
    // pelo Spring Boot 4.1 é do Jackson 3 (tools.jackson.databind), não dessa API
    // (com.fasterxml.jackson.databind, usada aqui por já ser a mesma do resto do
    // projeto). Pra um parsing pontual como esse, uma instância própria evita
    // acoplar o bean a qual major version do Jackson o Spring decidir expor.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            String registrationId = oauthToken.getAuthorizedClientRegistrationId();
            SocialProvider provider = toProvider(registrationId);

            // openid está no scope das três registrations, então o principal e sempre
            // um OidcUser — usamos os getters tipados (sub/email/email_verified) em
            // vez de ler atributos crus por chave de string.
            OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();
            String providerUserId = oidcUser.getSubject();
            String email = oidcUser.getEmail();
            // A Microsoft (AAD) nem sempre preenche email_verified no id_token/userinfo;
            // quando ausente, tratamos como não verificado (nunca linka por e-mail "no escuro").
            boolean emailVerified = Boolean.TRUE.equals(oidcUser.getEmailVerified());
            String fullName = resolveFullName(registrationId, request, oidcUser);

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
            String errorCode = toErrorCode(e);
            if ("social_login_failed".equals(errorCode)) {
                log.warn("Falha ao resolver/criar conta no login social: {}", e.getMessage(), e);
            } else {
                // Recusa esperada (cadastro fechado / fora do beta): não é falha do sistema.
                log.info("Cadastro via login social recusado ({}): {}", errorCode, e.getMessage());
            }
            response.sendRedirect(callbackUrlResolver.buildRedirectUrl(platform, "error", errorCode));
        }
    }

    /** Código de erro do redirect: o app usa para mostrar a mensagem certa. */
    private String toErrorCode(RuntimeException e) {
        if (e instanceof RegistrationClosedException) {
            return "registration_closed";
        }
        if (e instanceof RegistrationRestrictedException) {
            return "registration_restricted";
        }
        return "social_login_failed";
    }

    private String readSessionAttribute(HttpSession session, String attribute) {
        return session != null ? (String) session.getAttribute(attribute) : null;
    }

    private SocialProvider toProvider(String registrationId) {
        return switch (registrationId) {
            case "google" -> SocialProvider.GOOGLE;
            case "microsoft" -> SocialProvider.MICROSOFT;
            case "apple" -> SocialProvider.APPLE;
            default -> throw new IllegalStateException("Provedor social desconhecido: " + registrationId);
        };
    }

    // A Apple nunca manda "name" como claim do id_token — só manda um parametro de
    // formulario separado chamado "user" (JSON com firstName/lastName), e SO na
    // primeira autorizacao daquele usuario com esse client. Em qualquer login
    // seguinte o parametro simplesmente nao vem; SocialAuthService ja trata nome
    // ausente usando o local-part do e-mail como fallback.
    private String resolveFullName(String registrationId, HttpServletRequest request, OidcUser oidcUser) {
        if (!"apple".equals(registrationId)) {
            return oidcUser.getFullName();
        }

        String userParam = request.getParameter("user");
        if (userParam == null || userParam.isBlank()) {
            return null;
        }

        try {
            JsonNode name = OBJECT_MAPPER.readTree(userParam).path("name");
            String fullName = (name.path("firstName").asText("") + " " + name.path("lastName").asText("")).trim();
            return fullName.isBlank() ? null : fullName;
        } catch (JsonProcessingException e) {
            log.warn("Falha ao parsear parâmetro 'user' da Apple: {}", e.getMessage());
            return null;
        }
    }
}
