package com.beefit.api.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class RateLimitRepository {

    private final JdbcTemplate jdbcTemplate;

    public int increment(String bucketKey, Instant windowStartedAt, Instant expiresAt) {
        Integer count = jdbcTemplate.queryForObject("""
                INSERT INTO rate_limit_buckets (bucket_key, window_started_at, request_count, expires_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (bucket_key) DO UPDATE SET
                    window_started_at = EXCLUDED.window_started_at,
                    request_count = CASE
                        WHEN rate_limit_buckets.window_started_at = EXCLUDED.window_started_at
                            THEN rate_limit_buckets.request_count + 1
                        ELSE 1
                    END,
                    expires_at = EXCLUDED.expires_at
                RETURNING request_count
                """,
                Integer.class,
                bucketKey,
                Timestamp.from(windowStartedAt),
                Timestamp.from(expiresAt));
        return count == null ? 1 : count;
    }

    public int deleteExpired(Instant now) {
        return jdbcTemplate.update(
                "DELETE FROM rate_limit_buckets WHERE expires_at < ?",
                Timestamp.from(now));
    }
}
