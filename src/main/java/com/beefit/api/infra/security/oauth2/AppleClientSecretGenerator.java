package com.beefit.api.infra.security.oauth2;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * A Apple não usa um client secret estático: exige um JWT assinado com ES256,
 * usando a chave privada (.p8) baixada do Apple Developer, trocado a cada
 * requisição de token (ver AppleAwareTokenResponseClient). Claims exigidas pela
 * Apple: iss=Team ID, sub=Services ID (client_id), aud=https://appleid.apple.com,
 * header kid=Key ID.
 */
@Component
public class AppleClientSecretGenerator {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration SECRET_TTL = Duration.ofMinutes(5);

    @Value("${beefit.oauth2.apple.team-id}")
    String teamId;

    @Value("${beefit.oauth2.apple.key-id}")
    String keyId;

    @Value("${beefit.oauth2.apple.private-key-base64}")
    String privateKeyBase64;

    private ECPrivateKey privateKey;

    @PostConstruct
    void init() {
        this.privateKey = parsePrivateKey(privateKeyBase64);
    }

    public String generate(String clientId) {
        Instant now = Instant.now();
        return JWT.create()
                .withKeyId(keyId)
                .withIssuer(teamId)
                .withSubject(clientId)
                .withAudience(AUDIENCE)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(SECRET_TTL))
                .sign(Algorithm.ECDSA256(privateKey));
    }

    // O conteúdo do .p8 já é a chave PKCS8 em base64 (as linhas BEGIN/END PRIVATE
    // KEY só delimitam esse mesmo base64) — por isso não há re-codificação aqui,
    // só decodificação direta pro DER que o KeyFactory espera.
    private static ECPrivateKey parsePrivateKey(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Chave privada da Apple (beefit.oauth2.apple.private-key-base64) inválida.", e);
        }
    }
}
