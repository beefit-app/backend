package com.beefit.api.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.beefit.api.domain.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByRefreshTokenHashAndDeviceIdAndRevokedAtIsNull(String refreshTokenHash, String deviceId);

    List<AuthSession> findAllByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId,
                                                                                     String deviceId,
                                                                                     LocalDateTime now);
}
