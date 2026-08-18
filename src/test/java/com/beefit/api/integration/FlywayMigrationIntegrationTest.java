package com.beefit.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.beefit.api.support.PostgreSqlTestSupport;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport.createContainer("beefit_test");

    @Test
    void migratesLegacyCardsAndDeduplicatesOppositeFriendships() throws Exception {
        Flyway legacyFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .load();
        assertEquals(7, legacyFlyway.migrate().migrationsExecuted);

        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        UUID baselineWorkoutId = UUID.randomUUID();
        UUID improvedWorkoutId = UUID.randomUUID();
        UUID baselineSessionExerciseId = UUID.randomUUID();
        UUID improvedSessionExerciseId = UUID.randomUUID();
        UUID baselineCardId = UUID.randomUUID();
        UUID improvedCardId = UUID.randomUUID();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (
                        body_weight, cards_count, ghost_mode, offensive_honeys, weekly_goal,
                        weekly_workouts_count, workouts_count, id, full_name, email,
                        password_hash, role, username
                    ) VALUES (80, 2, false, 0, 3, 0, 2, '%s', 'Bee Test',
                        'bee@test.local', 'hash', 'USER', 'bee-test')
                    """.formatted(userId));
            statement.executeUpdate("INSERT INTO inventory (total_cards, id, user_id) VALUES (2, '%s', '%s')"
                    .formatted(inventoryId, userId));
            statement.executeUpdate("""
                    INSERT INTO users (
                        body_weight, cards_count, ghost_mode, offensive_honeys, weekly_goal,
                        weekly_workouts_count, workouts_count, id, full_name, email,
                        password_hash, role, username
                    ) VALUES (75, 0, false, 0, 3, 0, 0, '%s', 'Friend Test',
                        'friend@test.local', 'hash', 'USER', 'friend-test')
                    """.formatted(friendId));
            statement.executeUpdate("""
                    INSERT INTO friendship (id, requester_id, addressee_id, status, sent_at)
                    VALUES ('%s', '%s', '%s', 'PENDING', '2026-01-01 10:00:00'),
                           ('%s', '%s', '%s', 'ACCEPTED', '2026-01-02 10:00:00')
                    """.formatted(UUID.randomUUID(), userId, friendId,
                            UUID.randomUUID(), friendId, userId));
            statement.executeUpdate("""
                    INSERT INTO exercises (
                        id, name, primary_muscle_groups, secondary_muscle_groups,
                        primary_muscles, secondary_muscles
                    ) VALUES ('%s', 'Supino reto', ARRAY['CHEST'], ARRAY['ARMS'],
                        ARRAY['CHEST'], ARRAY['TRICEPS'])
                    """.formatted(exerciseId));
            statement.executeUpdate(workoutInsert(baselineWorkoutId, userId, "2026-01-01 10:00:00"));
            statement.executeUpdate(workoutInsert(improvedWorkoutId, userId, "2026-01-02 10:00:00"));
            statement.executeUpdate(sessionExerciseInsert(baselineSessionExerciseId, baselineWorkoutId, exerciseId));
            statement.executeUpdate(sessionExerciseInsert(improvedSessionExerciseId, improvedWorkoutId, exerciseId));
            statement.executeUpdate(setInsert(UUID.randomUUID(), baselineSessionExerciseId, 100.0, 5));
            statement.executeUpdate(setInsert(UUID.randomUUID(), improvedSessionExerciseId, 110.0, 5));
            statement.executeUpdate(cardInsert(baselineCardId, inventoryId, baselineWorkoutId, "2026-01-01 10:01:00"));
            statement.executeUpdate(cardInsert(improvedCardId, inventoryId, improvedWorkoutId, "2026-01-02 10:01:00"));
            statement.executeUpdate("UPDATE workout_sessions SET card_id = '%s' WHERE id = '%s'"
                    .formatted(baselineCardId, baselineWorkoutId));
            statement.executeUpdate("UPDATE workout_sessions SET card_id = '%s' WHERE id = '%s'"
                    .formatted(improvedCardId, improvedWorkoutId));
        }

        Flyway currentFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8"))
                .load();
        assertEquals(1, currentFlyway.migrate().migrationsExecuted);

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT exercise_id, best_set_weight_kg, best_set_reps,
                           estimated_one_rep_max, improvement_percentage, card_rarity,
                           muscle_intensities
                    FROM card
                    """)) {
                result.next();
                assertEquals(exerciseId, result.getObject("exercise_id", UUID.class));
                assertEquals(110.0, result.getDouble("best_set_weight_kg"));
                assertEquals(5, result.getInt("best_set_reps"));
                assertEquals(128.333, result.getDouble("estimated_one_rep_max"), 0.001);
                assertEquals(10.0, result.getDouble("improvement_percentage"), 0.001);
                assertEquals("RARE", result.getString("card_rarity"));
                assertEquals("{\"chest\": 4, \"triceps\": 2}", result.getString("muscle_intensities"));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM card")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
            try (var result = statement.executeQuery(
                    "SELECT cards_count FROM users WHERE id = '%s'".formatted(userId))) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }

        Flyway friendshipFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9"))
                .load();
        assertEquals(1, friendshipFlyway.migrate().migrationsExecuted);

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) AS relationship_count, MAX(status) AS remaining_status
                     FROM friendship
                     WHERE LEAST(requester_id, addressee_id) = LEAST('%s'::uuid, '%s'::uuid)
                       AND GREATEST(requester_id, addressee_id) = GREATEST('%s'::uuid, '%s'::uuid)
                     """.formatted(userId, friendId, userId, friendId))) {
            result.next();
            assertEquals(1, result.getInt("relationship_count"));
            assertEquals("ACCEPTED", result.getString("remaining_status"));
        }
    }

    private String workoutInsert(UUID workoutId, UUID userId, String finishedAt) {
        return """
                INSERT INTO workout_sessions (
                    duration_seconds, is_private, should_post_workout, total_volume,
                    finished_at, started_at, id, user_id, status, muscle_groups
                ) VALUES (3600, false, false, 1000, '%s', '%s'::timestamp - INTERVAL '1 hour',
                    '%s', '%s', 'FINISHED', ARRAY['CHEST'])
                """.formatted(finishedAt, finishedAt, workoutId, userId);
    }

    private String sessionExerciseInsert(UUID id, UUID workoutId, UUID exerciseId) {
        return "INSERT INTO session_exercises (exercise_order, exercise_id, id, workout_id) "
                + "VALUES (1, '%s', '%s', '%s')".formatted(exerciseId, id, workoutId);
    }

    private String setInsert(UUID id, UUID sessionExerciseId, double weight, int reps) {
        return "INSERT INTO sets (is_personal_record, reps, set_order, weight_kg, exercise_id, id) "
                + "VALUES (false, %d, 1, %s, '%s', '%s')".formatted(reps, weight, sessionExerciseId, id);
    }

    private String cardInsert(UUID id, UUID inventoryId, UUID workoutId, String generatedAt) {
        return """
                INSERT INTO card (
                    attack_power, body_weight_at_generation, is_personal_record,
                    generated_at, id, inventory_id, workout_session_id,
                    card_rarity, muscle_group, muscle_intensities
                ) VALUES (100, 80, false, '%s', '%s', '%s', '%s',
                    'COMMON', 'CHEST', '{"chest": 4}'::jsonb)
                """.formatted(generatedAt, id, inventoryId, workoutId);
    }
}
