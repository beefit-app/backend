package com.beefit.api.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.beefit.api.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {

    public enum TokenValidationStatus {
        VALID,
        EXPIRED,
        INVALID,
        MISSING
    }

    public record TokenValidationResult(TokenValidationStatus status, String subject) {
    }

    @Value("${beefit.security.token.secret}")
    String secret;

    @Value("${beefit.security.token.access-token-minutes:15}")
    Integer accessTokenDurationMinutes;

    public String generateToken(User user){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String token = JWT.create()
                    .withIssuer("auth-beefit")
                    .withSubject(user.getEmail())
                    .withClaim("active", !user.isBanned())
                    .withExpiresAt(generateExpirationDate())
                    .sign(algorithm);

            return token;
        }catch(JWTCreationException jwtCreationException){
            throw new RuntimeException("Error while generating token", jwtCreationException);
        }
    }

    public TokenValidationResult validateToken(String token){
        if (token == null || token.isBlank()) {
            return new TokenValidationResult(TokenValidationStatus.MISSING, null);
        }

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String subject = JWT.require(algorithm)
                    .withIssuer("auth-beefit")
                    .build()
                    .verify(token)
                    .getSubject();
            return new TokenValidationResult(TokenValidationStatus.VALID, subject);
        } catch (TokenExpiredException tokenExpiredException) {
            return new TokenValidationResult(TokenValidationStatus.EXPIRED, null);
        } catch(JWTVerificationException jwtVerificationException){
            return new TokenValidationResult(TokenValidationStatus.INVALID, null);
        }
    }

    public Instant generateExpirationDate(){
        return LocalDateTime.now().plusMinutes(accessTokenDurationMinutes).toInstant(ZoneOffset.of("-03:00"));
    }
}
