package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.beefit.api.domain.User;
import com.beefit.api.domain.enums.UserRole;

class TokenServiceTest {

    private final TimeZone originalTimeZone = TimeZone.getDefault();

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    private TokenService tokenService() {
        TokenService tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "segredo-de-teste-com-tamanho-suficiente");
        ReflectionTestUtils.setField(tokenService, "accessTokenDurationMinutes", 15);
        return tokenService;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alguem@example.com");
        user.setRole(UserRole.USER);
        return user;
    }

    // O bug corrigido: generateExpirationDate lia a hora de parede do fuso default
    // e a reinterpretava como -03:00, entao a validade do token mudava conforme o
    // fuso da maquina. Com a JVM em UTC o token de 15 min durava 3h15.
    @ParameterizedTest
    @ValueSource(strings = { "UTC", "America/Sao_Paulo", "Asia/Tokyo", "America/Los_Angeles" })
    void expirationIsFifteenMinutesAheadRegardlessOfDefaultTimeZone(String zoneId) {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));

        Instant antes = Instant.now();
        Instant expiracao = tokenService().generateExpirationDate();
        Instant depois = Instant.now();

        assertTrue(expiracao.isAfter(antes.plus(Duration.ofMinutes(14))),
                "expiracao cedo demais no fuso " + zoneId + ": " + expiracao);
        assertTrue(expiracao.isBefore(depois.plus(Duration.ofMinutes(16))),
                "expiracao tarde demais no fuso " + zoneId + ": " + expiracao);
    }

    @Test
    void generatedTokenCarriesSubjectAndSessionId() {
        TokenService tokenService = tokenService();
        User user = user();
        UUID sessionId = UUID.randomUUID();

        var resultado = tokenService.validateToken(tokenService.generateToken(user, sessionId));

        assertEquals(TokenService.TokenValidationStatus.VALID, resultado.status());
        assertEquals(user.getEmail(), resultado.subject());
        assertEquals(sessionId, resultado.sessionId());
    }

    @Test
    void tokenSignedWithAnotherSecretIsInvalid() {
        TokenService emissor = tokenService();
        TokenService verificador = new TokenService();
        ReflectionTestUtils.setField(verificador, "secret", "outro-segredo-completamente-diferente");
        ReflectionTestUtils.setField(verificador, "accessTokenDurationMinutes", 15);

        var resultado = verificador.validateToken(emissor.generateToken(user(), UUID.randomUUID()));

        assertEquals(TokenService.TokenValidationStatus.INVALID, resultado.status());
        assertNull(resultado.sessionId());
    }

    @Test
    void missingTokenIsReportedAsMissing() {
        assertEquals(TokenService.TokenValidationStatus.MISSING, tokenService().validateToken(null).status());
        assertEquals(TokenService.TokenValidationStatus.MISSING, tokenService().validateToken("  ").status());
    }

    @Test
    void rejectsWeakJwtSecretAtStartup() {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "secret", "weak-secret");
        ReflectionTestUtils.setField(service, "accessTokenDurationMinutes", 15);
        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }
}
