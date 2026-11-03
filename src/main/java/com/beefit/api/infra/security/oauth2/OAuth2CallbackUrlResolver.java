package com.beefit.api.infra.security.oauth2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Resolve, a partir do "platform" (web|mobile) propagado pela sessão (ver
 * {@link OAuth2PlatformAwareAuthorizationRequestResolver}), para onde o
 * success/failure handler do login social deve redirecionar o usuário.
 */
@Component
public class OAuth2CallbackUrlResolver {

    @Value("${beefit.oauth2.callback-base-web}")
    private String callbackBaseWeb;

    @Value("${beefit.oauth2.callback-base-mobile}")
    private String callbackBaseMobile;

    public String buildRedirectUrl(String platform, String paramName, String paramValue) {
        String base = "mobile".equals(platform) ? callbackBaseMobile : callbackBaseWeb;
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + paramName + "=" + URLEncoder.encode(paramValue, StandardCharsets.UTF_8);
    }
}
