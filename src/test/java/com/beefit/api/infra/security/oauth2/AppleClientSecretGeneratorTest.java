package com.beefit.api.infra.security.oauth2;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppleClientSecretGeneratorTest {

    @Test
    void generatesEs256JwtWithAppleRequiredClaims() throws NoSuchAlgorithmException {
        KeyPair keyPair = generateEcKeyPair();
        AppleClientSecretGenerator generator = newGenerator(
                "TEAM1234ID", "KEY1234ID", (ECPrivateKey) keyPair.getPrivate());

        Instant before = Instant.now();
        String secret = generator.generate("com.beefit.app.services");
        Instant after = Instant.now();

        DecodedJWT decoded = JWT.decode(secret);
        assertEquals("ES256", decoded.getAlgorithm());
        assertEquals("KEY1234ID", decoded.getKeyId());
        assertEquals("TEAM1234ID", decoded.getIssuer());
        assertEquals("com.beefit.app.services", decoded.getSubject());
        assertEquals("https://appleid.apple.com", decoded.getAudience().getFirst());

        assertTrue(!decoded.getIssuedAtAsInstant().isBefore(before.minusSeconds(1)));
        assertTrue(!decoded.getIssuedAtAsInstant().isAfter(after.plusSeconds(1)));
        assertTrue(decoded.getExpiresAtAsInstant().isAfter(decoded.getIssuedAtAsInstant()));
        assertTrue(decoded.getExpiresAtAsInstant().minusSeconds(decoded.getIssuedAtAsInstant().getEpochSecond())
                .getEpochSecond() <= 300);

        // Assinatura precisa ser verificável com a chave pública do mesmo par —
        // confirma que a chave privada foi parseada e usada corretamente.
        Algorithm verifier = Algorithm.ECDSA256((ECPublicKey) keyPair.getPublic(), null);
        JWT.require(verifier).build().verify(secret);
    }

    private AppleClientSecretGenerator newGenerator(String teamId, String keyId, ECPrivateKey privateKey) {
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator();
        ReflectionTestUtils.setField(generator, "teamId", teamId);
        ReflectionTestUtils.setField(generator, "keyId", keyId);
        ReflectionTestUtils.setField(generator, "privateKey", privateKey);
        return generator;
    }

    private KeyPair generateEcKeyPair() throws NoSuchAlgorithmException {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            return keyPairGenerator.generateKeyPair();
        } catch (java.security.InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void base64EncodedPkcs8KeyRoundTripsThroughParsePrivateKey() throws NoSuchAlgorithmException {
        KeyPair keyPair = generateEcKeyPair();
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        AppleClientSecretGenerator generator = new AppleClientSecretGenerator();
        ReflectionTestUtils.setField(generator, "teamId", "TEAM1234ID");
        ReflectionTestUtils.setField(generator, "keyId", "KEY1234ID");
        ReflectionTestUtils.setField(generator, "privateKeyBase64", base64);
        ReflectionTestUtils.invokeMethod(generator, "init");

        String secret = generator.generate("com.beefit.app.services");
        DecodedJWT decoded = JWT.decode(secret);
        assertEquals("ES256", decoded.getAlgorithm());
    }
}
