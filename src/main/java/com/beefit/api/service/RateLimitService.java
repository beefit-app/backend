package com.beefit.api.service;

import com.beefit.api.exception.security.RateLimitExceededException;
import com.beefit.api.repository.RateLimitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class RateLimitService {

    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(5);
    private static final Duration PASSWORD_WINDOW = Duration.ofHours(1);
    private static final Duration UPLOAD_WINDOW = Duration.ofHours(1);

    private final RateLimitRepository repository;
    private final int registrationMaximum;

    public RateLimitService(
            RateLimitRepository repository,
            @Value("${beefit.security.rate-limit.registration-maximum:5}") int registrationMaximum) {
        this.repository = repository;
        this.registrationMaximum = registrationMaximum;
    }

    public void checkLogin(String remoteAddress, String email) {
        check("login-ip", remoteAddress, 10, LOGIN_WINDOW);
        check("login-account", normalizeEmail(email), 5, LOGIN_WINDOW);
    }

    public void checkRegistration(String remoteAddress) {
        check("register-ip", remoteAddress, registrationMaximum, REGISTER_WINDOW);
    }

    public void checkRefresh(String deviceId) {
        check("refresh-device", deviceId, 30, REFRESH_WINDOW);
    }

    public void checkPasswordChange(UUID userId) {
        check("password-user", userId.toString(), 5, PASSWORD_WINDOW);
    }

    public void checkUpload(UUID userId) {
        check("upload-user", userId.toString(), 20, UPLOAD_WINDOW);
    }

    private void check(String scope, String identity, int maximumRequests, Duration window) {
        Instant now = Instant.now();
        long windowSeconds = window.toSeconds();
        Instant windowStartedAt = Instant.ofEpochSecond((now.getEpochSecond() / windowSeconds) * windowSeconds);
        Instant expiresAt = windowStartedAt.plus(window).plus(Duration.ofMinutes(1));
        String bucketKey = scope + ":" + hash(identity == null ? "" : identity);

        int requestCount = repository.increment(bucketKey, windowStartedAt, expiresAt);
        if (requestCount > maximumRequests) {
            long retryAfter = Math.max(1, windowStartedAt.plus(window).getEpochSecond() - now.getEpochSecond());
            throw new RateLimitExceededException(retryAfter);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupExpiredBuckets() {
        repository.deleteExpired(Instant.now());
    }
}
