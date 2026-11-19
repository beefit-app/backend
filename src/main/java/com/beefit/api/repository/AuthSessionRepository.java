package com.beefit.api.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    // O lock de escrita é na conta, não no token: a linha permanece a mesma
    // durante rotações. Esta consulta só lê identificadores imutáveis da sessão.
    @Query(value = """
            SELECT u.id AS "userId", s.family_id AS "familyId"
            FROM auth_sessions s JOIN users u ON u.id = s.user_id
            WHERE s.refresh_token_hash = :hash AND s.device_id = :deviceId
            FOR NO KEY UPDATE OF u
            """, nativeQuery = true)
    Optional<SessionFamily> lockOwnerByRefreshToken(@Param("hash") String hash, @Param("deviceId") String deviceId);

    @Query(value = "SELECT id FROM users WHERE lower(email) = :email FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockOwnerByEmail(@Param("email") String email);

    interface SessionFamily {
        UUID getUserId();
        UUID getFamilyId();
    }
    Optional<AuthSession> findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(String refreshTokenHash, String deviceId);

    List<AuthSession> findAllByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId,
                                                                                     String deviceId,
                                                                                     LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE AuthSession s SET s.revokedAt = :now, s.lastUsedAt = :now
            WHERE s.user.id = :userId AND s.revokedAt IS NULL
            """)
    void revokeAllForOwner(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE AuthSession s SET s.revokedAt = :now, s.lastUsedAt = :now
            WHERE s.user.id = :userId AND s.familyId = :familyId AND s.revokedAt IS NULL
            """)
    void revokeFamily(@Param("userId") UUID userId, @Param("familyId") UUID familyId, @Param("now") LocalDateTime now);

    // Os disparos pós-commit ainda podem ter a transação original vinculada
    // à thread. Não honre seu contexto de persistência nem seu snapshot.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    @Query("""
            SELECT s.id AS id, u.id AS userId, s.expiresAt AS expiresAt
            FROM AuthSession s JOIN s.user u
            WHERE s.id IN :ids AND s.revokedAt IS NULL AND s.expiresAt > :now
              AND (u.bannedUntil IS NULL OR u.bannedUntil <= :now)
            """)
    List<StreamSession> findActiveStreamSessions(@Param("ids") Set<UUID> ids, @Param("now") LocalDateTime now);

    interface StreamSession {
        UUID getId();
        UUID getUserId();
        LocalDateTime getExpiresAt();
    }
}
