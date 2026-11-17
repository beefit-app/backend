package com.beefit.api.infra.security.oauth2;

import com.beefit.api.dto.auth.LoginResponseDTO;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda, em memória, a sessão (JWT + refresh token) emitida ao final de um login
 * social, atrás de um código de troca opaco de uso único e curta duração.
 *
 * Isso evita devolver os tokens da aplicação diretamente na URL do redirect final
 * (histórico de navegador, logs de proxy, header Referrer) — o front troca o código
 * por tokens reais numa chamada POST normal (/auth/social/exchange).
 *
 * Só funciona com uma única instância do backend em memória; se o serviço passar a
 * rodar com múltiplas instâncias sem sticky session, isso precisa migrar para uma
 * tabela com TTL.
 */
@Component
public class SocialExchangeCodeStore {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(LoginResponseDTO session) {
        Instant now = Instant.now();
        // Códigos emitidos e nunca trocados (usuário fechou o app no meio do fluxo)
        // ficariam no mapa pra sempre; a varredura é barata porque o TTL é curto.
        codes.values().removeIf(entry -> entry.expiresAt().isBefore(now));
        String code = generateCode();
        codes.put(code, new Entry(session, now.plus(TTL)));
        return code;
    }

    /** Uso único: o código some do mapa seja ele válido ou não. */
    public Optional<LoginResponseDTO> consume(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Entry entry = codes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.session());
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Entry(LoginResponseDTO session, Instant expiresAt) {
    }
}
