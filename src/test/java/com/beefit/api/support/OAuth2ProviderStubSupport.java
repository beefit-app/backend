package com.beefit.api.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Simula, via WireMock, os endpoints OIDC de um provedor (token/jwks/userinfo) para
 * os testes de integração do login social — sem bater nos servidores reais do
 * Google/Microsoft. Os id_tokens são assinados com uma chave RSA gerada em memória,
 * cuja chave pública é servida pelo endpoint de JWKS stubado.
 */
public final class OAuth2ProviderStubSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WireMockServer server;
    private final RSAKey rsaKey;

    public OAuth2ProviderStubSupport(WireMockServer server) {
        this.server = server;
        this.rsaKey = generateRsaKey();
    }

    /** Precisa ser chamado de novo depois de qualquer WireMock#resetAll(). */
    public void stubJwksEndpoints(String... providerPaths) {
        try {
            String jwksJson = JSON.writeValueAsString(new JWKSet(rsaKey.toPublicJWK()).toJSONObject());
            for (String providerPath : providerPaths) {
                server.stubFor(get(urlEqualTo("/" + providerPath + "/jwks")).willReturn(okJson(jwksJson)));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar JWKS de teste.", e);
        }
    }

    public void stubTokenExchange(String providerPath, String idToken) {
        server.stubFor(post(urlEqualTo("/" + providerPath + "/token")).willReturn(okJson("""
                {"access_token":"test-access-token","token_type":"Bearer","expires_in":3600,
                 "scope":"openid profile email","id_token":"%s"}
                """.formatted(idToken))));
    }

    public void stubTokenExchangeFailure(String providerPath) {
        server.stubFor(post(urlEqualTo("/" + providerPath + "/token"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    public void stubUserInfo(String providerPath, String subject) {
        server.stubFor(get(urlEqualTo("/" + providerPath + "/userinfo"))
                .willReturn(okJson("{\"sub\":\"" + subject + "\"}")));
    }

    public String signIdToken(String issuer, String audience, String subject, String email,
            Boolean emailVerified, String name, String nonce) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject(subject)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("nonce", nonce);
            if (email != null) {
                claims.claim("email", email);
            }
            if (emailVerified != null) {
                claims.claim("email_verified", emailVerified);
            }
            if (name != null) {
                claims.claim("name", name);
            }

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar id_token de teste.", e);
        }
    }

    private static RSAKey generateRsaKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key-1").algorithm(JWSAlgorithm.RS256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao gerar chave RSA de teste.", e);
        }
    }
}
