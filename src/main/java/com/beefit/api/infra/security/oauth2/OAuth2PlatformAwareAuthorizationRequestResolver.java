package com.beefit.api.infra.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Propaga "platform" (web|mobile) e "deviceId", mandados como query params na
 * requisição inicial (GET /oauth2/authorization/{provider}?platform=...&deviceId=...),
 * para a HttpSession — de onde o {@link OAuth2LoginSuccessHandler}/
 * {@link OAuth2LoginFailureHandler} os leem depois do callback do provedor.
 *
 * Não usa o parâmetro "state" do OAuth2 pra isso: esse é gerenciado internamente
 * pelo Spring Security como proteção CSRF do fluxo, não é o lugar certo para dados
 * de aplicação.
 */
public class OAuth2PlatformAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String PLATFORM_SESSION_ATTRIBUTE = "beefit.oauth2.platform";
    public static final String DEVICE_ID_SESSION_ATTRIBUTE = "beefit.oauth2.deviceId";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public OAuth2PlatformAwareAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        captureRequestParams(request);
        return delegate.resolve(request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        captureRequestParams(request);
        return delegate.resolve(request, clientRegistrationId);
    }

    private void captureRequestParams(HttpServletRequest request) {
        String platform = request.getParameter("platform");
        String deviceId = request.getParameter("deviceId");
        if (platform == null && deviceId == null) {
            return;
        }
        HttpSession session = request.getSession();
        if (platform != null) {
            session.setAttribute(PLATFORM_SESSION_ATTRIBUTE, platform);
        }
        if (deviceId != null) {
            session.setAttribute(DEVICE_ID_SESSION_ATTRIBUTE, deviceId);
        }
    }
}
