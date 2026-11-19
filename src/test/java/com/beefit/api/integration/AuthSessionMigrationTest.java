package com.beefit.api.integration;

import com.beefit.api.support.PostgreSqlTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
@Timeout(30)
class AuthSessionMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport.createContainer("auth_session_migration_test");

    @Test
    void preservesLegacySessionsAndDoesNotInferLineageFromDevice() throws Exception {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        configuration.target(MigrationVersion.fromVersion("19")).load().migrate();

        var user = UUID.randomUUID();
        var active = UUID.randomUUID();
        var revoked = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var insertUser = connection.prepareStatement("""
                        INSERT INTO users (id, full_name, email, username, password_hash, body_weight, weekly_goal,
                                           ghost_mode, workouts_count, weekly_workouts_count, cards_count, offensive_honeys, role)
                        VALUES (?, 'Session Fixture', 'session-fixture@example.test', 'session-fixture', 'fixture-hash', 70, 3,
                                false, 0, 0, 0, 0, 'USER')
                        """);
                var insertSession = connection.prepareStatement("""
                        INSERT INTO auth_sessions (id, user_id, device_id, refresh_token_hash, created_at,
                                                   expires_at, last_used_at, revoked_at)
                        VALUES (?, ?, 'device-test', ?, LOCALTIMESTAMP, LOCALTIMESTAMP + INTERVAL '1 day',
                                LOCALTIMESTAMP, CASE WHEN ? THEN LOCALTIMESTAMP END)
                        """)) {
            insertUser.setObject(1, user);
            insertUser.executeUpdate();
            insertSession.setObject(1, active);
            insertSession.setObject(2, user);
            insertSession.setString(3, "a".repeat(64));
            insertSession.setBoolean(4, false);
            insertSession.executeUpdate();
            insertSession.setObject(1, revoked);
            insertSession.setString(3, "b".repeat(64));
            insertSession.setBoolean(4, true);
            insertSession.executeUpdate();
        }

        configuration.target(MigrationVersion.LATEST).load().migrate();

        Map<UUID, LegacySession> actual = new HashMap<>();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT id, family_id, refresh_token_hash, revoked_at IS NOT NULL FROM auth_sessions")) {
            while (rows.next()) {
                actual.put(rows.getObject(1, UUID.class),
                        new LegacySession(rows.getObject(2, UUID.class), rows.getString(3), rows.getBoolean(4)));
            }
        }
        assertEquals(Map.of(active, new LegacySession(active, "a".repeat(64), false),
                revoked, new LegacySession(revoked, "b".repeat(64), true)), actual);
    }

    private record LegacySession(UUID familyId, String hash, boolean revoked) {}
}
