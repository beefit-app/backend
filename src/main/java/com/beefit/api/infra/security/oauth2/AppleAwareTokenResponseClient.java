package com.beefit.api.infra.security.oauth2;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

/**
 * A troca de token do Google/Microsoft usa client secret estático (client_id +
 * client_secret fixos). A Apple exige um client secret gerado na hora — um JWT
 * assinado com ES256 (ver AppleClientSecretGenerator), de curta duração. Esse
 * client intercepta só o registration "apple": clona a ClientRegistration com o
 * secret recém-gerado antes de delegar pro client padrão do Spring; Google e
 * Microsoft passam direto, sem nenhuma alteração.
 */
@Component
@RequiredArgsConstructor
public class AppleAwareTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final String APPLE_REGISTRATION_ID = "apple";

    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> delegate =
            new RestClientAuthorizationCodeTokenResponseClient();

    private final AppleClientSecretGenerator appleClientSecretGenerator;

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest request) {
        ClientRegistration registration = request.getClientRegistration();
        if (!APPLE_REGISTRATION_ID.equals(registration.getRegistrationId())) {
            return delegate.getTokenResponse(request);
        }

        ClientRegistration withDynamicSecret = ClientRegistration.withClientRegistration(registration)
                .clientSecret(appleClientSecretGenerator.generate(registration.getClientId()))
                .build();

        return delegate.getTokenResponse(
                new OAuth2AuthorizationCodeGrantRequest(withDynamicSecret, request.getAuthorizationExchange()));
    }
}
