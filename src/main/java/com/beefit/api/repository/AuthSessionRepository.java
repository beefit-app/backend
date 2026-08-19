package com.beefit.api.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.beefit.api.domain.AuthSession;

import jakarta.persistence.LockModeType;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(String refreshTokenHash, String deviceId);

    List<AuthSession> findAllByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId,
                                                                                     String deviceId,
                                                                                     LocalDateTime now);

    List<AuthSession> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
