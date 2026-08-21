package com.beefit.api.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.beefit.api.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TokenService {

    public enum TokenValidationStatus {
        VALID,
        EXPIRED,
        INVALID,
        MISSING
    }

    public record TokenValidationResult(TokenValidationStatus status, String subject, UUID sessionId) {
    }

    // Nome curto por convencao de JWT; guarda o id da AuthSession que emitiu o
    // token, para o SecurityFilter poder recusar token de sessao ja revogada.
    private static final String SESSION_CLAIM = "sid";

    @Value("${beefit.security.token.secret}")
    String secret;

    @Value("${beefit.security.token.access-token-minutes:15}")
    Integer accessTokenDurationMinutes;

    @PostConstruct
    void validateConfiguration() {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // Ausente, em branco e curto demais eram indistinguiveis na mensagem, o que
            // torna impossivel saber se a variavel chegou ao processo. O tamanho nao
            // revela nada sobre o segredo em si.
            String observado = secret == null
                    ? "nenhum valor"
                    : secret.getBytes(StandardCharsets.UTF_8).length + " bytes";
            throw new IllegalStateException(
                    "JWT_SECRET deve conter pelo menos 32 bytes aleatórios; recebido: " + observado + ".");
        }
        if (accessTokenDurationMinutes == null || accessTokenDurationMinutes < 1 || accessTokenDurationMinutes > 60) {
            throw new IllegalStateException("A validade do access token deve estar entre 1 e 60 minutos.");
        }
    }

    public String generateToken(User user, UUID sessionId){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String token = JWT.create()
                    .withIssuer("auth-beefit")
                    .withSubject(user.getEmail())
                    .withClaim("active", !user.isBanned())
                    .withClaim(SESSION_CLAIM, sessionId.toString())
                    .withExpiresAt(generateExpirationDate())
                    .sign(algorithm);

            return token;
        }catch(JWTCreationException jwtCreationException){
            throw new RuntimeException("Error while generating token", jwtCreationException);
        }
    }

    public TokenValidationResult validateToken(String token){
        if (token == null || token.isBlank()) {
            return new TokenValidationResult(TokenValidationStatus.MISSING, null, null);
        }

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            DecodedJWT decoded = JWT.require(algorithm)
                    .withIssuer("auth-beefit")
                    .build()
                    .verify(token);

            // Token sem sid nao da para amarrar a uma sessao, entao nao da para
            // saber se ja foi revogado: recusa em vez de aceitar sem verificacao.
            UUID sessionId = readSessionId(decoded);
            if (sessionId == null) {
                return new TokenValidationResult(TokenValidationStatus.INVALID, null, null);
            }

            return new TokenValidationResult(TokenValidationStatus.VALID, decoded.getSubject(), sessionId);
        } catch (TokenExpiredException tokenExpiredException) {
            return new TokenValidationResult(TokenValidationStatus.EXPIRED, null, null);
        } catch(JWTVerificationException jwtVerificationException){
            return new TokenValidationResult(TokenValidationStatus.INVALID, null, null);
        }
    }

    private UUID readSessionId(DecodedJWT decoded) {
        String rawSessionId = decoded.getClaim(SESSION_CLAIM).asString();
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawSessionId);
        } catch (IllegalArgumentException malformedSessionId) {
            return null;
        }
    }

    // Aritmetica de instante, sem fuso: a versao anterior pegava a hora de parede
    // do fuso default da JVM com LocalDateTime.now() e reinterpretava o resultado
    // como -03:00. Com a JVM em UTC — o padrao em container — isso empurrava a
    // expiracao 3 horas para frente, e o token de 15 minutos durava 3h15.
    public Instant generateExpirationDate(){
        return Instant.now().plus(Duration.ofMinutes(accessTokenDurationMinutes));
    }
}
