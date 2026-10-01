package com.beefit.api.repository;

import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.enums.WorkoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.beefit.api.dto.workout.ExerciseBestScoreProjection;
import com.beefit.api.dto.workout.ExerciseHistorySummaryProjection;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WorkoutSession w WHERE w.id = :id")
    Optional<WorkoutSession> findByIdForFinish(@Param("id") UUID id);

    Optional<WorkoutSession> findByUserIdAndFinishIdempotencyKey(UUID userId, String finishIdempotencyKey);

    // O Spring DATA JPA consegue fazer uma análise do nome da função e montar a query sozinho
    // Ele faz o caminho: WorkoutSession -> User -> id e monta a query:
    // SELECT * FROM workout_sessions WHERE user_id = {Valor passado}
    // Pesquisem por "JPA query methods" na documentação do Spring Data JPA
    List<WorkoutSession> findAllByUserId(UUID id);

    // Esse and é reservado e é traduzido na query também
    // Esse metodo é capaz de selecionar só os treinos ativos ou finalizados de um certo usuário
    List<WorkoutSession> findByStatusAndUserId(WorkoutStatus status, UUID id);

    @Query("""
    SELECT w FROM WorkoutSession w
    JOIN FETCH w.user
    WHERE w.status = :status
      AND w.isPrivate = false
      AND w.user.ghostMode = false
      AND w.user.id <> :currentUserId
      AND EXISTS (
          SELECT 1 FROM Friendship f
          WHERE f.status = com.beefit.api.domain.enums.FriendshipStatus.ACCEPTED
            AND ((f.requester.id = :currentUserId AND f.addressee.id = w.user.id)
              OR (f.addressee.id = :currentUserId AND f.requester.id = w.user.id))
      )
    ORDER BY w.startedAt DESC
    """)
    List<WorkoutSession> findAllVisibleToFriendByStatusExcludingUser(
            @Param("status") WorkoutStatus status,
            @Param("currentUserId") UUID currentUserId
    );

    @Query("""
    SELECT MAX(w.totalVolume) FROM WorkoutSession w WHERE w.user.id = :userId AND w.status = :status
    """)
    Optional<Double> findMaxTotalVolumeByUserId(@Param("userId") UUID userId, @Param("status") WorkoutStatus status);

    List<WorkoutSession> findTop10ByUserIdAndStatusOrderByFinishedAtDesc(UUID userId, WorkoutStatus status);

    @Query("""
            SELECT sessionExercise FROM SessionExercise sessionExercise
            JOIN FETCH sessionExercise.workoutSession workout
            JOIN FETCH sessionExercise.sets
            WHERE workout.user.id = :userId
              AND workout.status = :status
              AND sessionExercise.exercise.id = :catalogExerciseId
            ORDER BY workout.finishedAt DESC
            """)
    List<SessionExercise> findFinishedExercisesWithSetsByUserIdAndCatalogExerciseId(
            @Param("userId") UUID userId,
            @Param("catalogExerciseId") UUID catalogExerciseId,
            @Param("status") WorkoutStatus status);

    @Query(value = """
            SELECT MAX(s.weight_kg * (1.0 + LEAST(s.reps, 10) / 30.0))
            FROM sets s
            JOIN session_exercises se ON se.id = s.exercise_id
            JOIN workout_sessions ws ON ws.id = se.workout_id
            WHERE ws.user_id = :userId
              AND se.exercise_id = :exerciseId
              AND ws.id <> :currentWorkoutId
              AND ws.status = 'FINISHED'
              AND s.weight_kg > 0
              AND s.reps > 0
            """, nativeQuery = true)
    Optional<Double> findBestEstimatedOneRepMaxBeforeWorkout(
            @Param("userId") UUID userId,
            @Param("exerciseId") UUID exerciseId,
            @Param("currentWorkoutId") UUID currentWorkoutId);

    @Query(value = """
            SELECT MAX(s.reps)
            FROM sets s
            JOIN session_exercises se ON se.id = s.exercise_id
            JOIN workout_sessions ws ON ws.id = se.workout_id
            WHERE ws.user_id = :userId
              AND se.exercise_id = :exerciseId
              AND ws.id <> :currentWorkoutId
              AND ws.status = 'FINISHED'
              AND s.weight_kg = 0
              AND s.reps > 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM sets weighted_set
                  WHERE weighted_set.exercise_id = se.id
                    AND weighted_set.weight_kg > 0
                    AND weighted_set.reps > 0
              )
            """, nativeQuery = true)
    Optional<Integer> findBestBodyweightRepsBeforeWorkout(
            @Param("userId") UUID userId,
            @Param("exerciseId") UUID exerciseId,
            @Param("currentWorkoutId") UUID currentWorkoutId);

    @Query(value = """
            SELECT se.exercise_id AS exerciseId,
                   MAX(s.weight_kg * (1.0 + LEAST(s.reps, 10) / 30.0)) AS bestScore
            FROM sets s
            JOIN session_exercises se ON se.id = s.exercise_id
            JOIN workout_sessions ws ON ws.id = se.workout_id
            WHERE ws.user_id = :userId
              AND se.exercise_id IN (:exerciseIds)
              AND ws.id <> :currentWorkoutId
              AND ws.status = 'FINISHED'
              AND s.weight_kg > 0
              AND s.reps > 0
            GROUP BY se.exercise_id
            """, nativeQuery = true)
    List<ExerciseBestScoreProjection> findBestEstimatedOneRepMaxBatchBeforeWorkout(
            @Param("userId") UUID userId,
            @Param("exerciseIds") Collection<UUID> exerciseIds,
            @Param("currentWorkoutId") UUID currentWorkoutId);

    @Query(value = """
            SELECT se.exercise_id AS exerciseId,
                   MAX(CAST(s.reps AS double precision)) AS bestScore
            FROM sets s
            JOIN session_exercises se ON se.id = s.exercise_id
            JOIN workout_sessions ws ON ws.id = se.workout_id
            WHERE ws.user_id = :userId
              AND se.exercise_id IN (:exerciseIds)
              AND ws.id <> :currentWorkoutId
              AND ws.status = 'FINISHED'
              AND s.weight_kg = 0
              AND s.reps > 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM sets weighted_set
                  WHERE weighted_set.exercise_id = se.id
                    AND weighted_set.weight_kg > 0
                    AND weighted_set.reps > 0
              )
            GROUP BY se.exercise_id
            """, nativeQuery = true)
    List<ExerciseBestScoreProjection> findBestBodyweightRepsBatchBeforeWorkout(
            @Param("userId") UUID userId,
            @Param("exerciseIds") Collection<UUID> exerciseIds,
            @Param("currentWorkoutId") UUID currentWorkoutId);

    @Query(value = """
            WITH ranked AS (
                SELECT ws.finished_at AS finishedAt,
                       s.weight_kg AS weightKg,
                       s.reps AS reps,
                       ROW_NUMBER() OVER (ORDER BY ws.finished_at DESC, s.set_order DESC) AS rnLatest,
                       ROW_NUMBER() OVER (ORDER BY s.weight_kg DESC, s.reps DESC) AS rnBest
                FROM sets s
                JOIN session_exercises se ON se.id = s.exercise_id
                JOIN workout_sessions ws ON ws.id = se.workout_id
                WHERE ws.user_id = :userId
                  AND ws.status = 'FINISHED'
                  AND se.exercise_id = :catalogExerciseId
                  AND s.reps > 0
                  AND s.weight_kg >= 0
            )
            SELECT finishedAt, weightKg, reps, rnLatest, rnBest
            FROM ranked
            WHERE rnLatest = 1 OR rnBest = 1
            """, nativeQuery = true)
    List<ExerciseHistorySummaryProjection> findExerciseHistorySummary(
            @Param("userId") UUID userId,
            @Param("catalogExerciseId") UUID catalogExerciseId);

    // Um Honey representa um dia com pelo menos um treino elegivel. O treino que
    // esta sendo finalizado e excluido porque a query pode provocar flush antes
    // de ser executada.
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM WorkoutSession w WHERE w.user.id = :userId " +
            "AND w.id <> :currentWorkoutId AND w.status = :status " +
            "AND w.durationSeconds >= :minimumDurationSeconds " +
            "AND w.finishedAt >= :startOfDay AND w.finishedAt < :endOfDay " +
            "AND EXISTS (SELECT 1 FROM SessionExercise se JOIN se.sets s " +
            "WHERE se.workoutSession = w AND s.reps > 0)")
    boolean existsEligibleFinishedWorkoutOnDay(
            @Param("userId") UUID userId,
            @Param("currentWorkoutId") UUID currentWorkoutId,
            @Param("status") WorkoutStatus status,
            @Param("minimumDurationSeconds") int minimumDurationSeconds,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Query("SELECT DISTINCT w.finishedAt FROM WorkoutSession w WHERE w.user.id = :userId " +
            "AND w.status = :status AND w.durationSeconds >= :minimumDurationSeconds " +
            "AND w.finishedAt >= :start AND w.finishedAt < :end " +
            "AND EXISTS (SELECT 1 FROM SessionExercise se JOIN se.sets s " +
            "WHERE se.workoutSession = w AND s.reps > 0)")
    List<LocalDateTime> findFinishedDaysByUserAndMonth(@Param("userId") UUID userId, @Param("status") WorkoutStatus status,
            @Param("minimumDurationSeconds") int minimumDurationSeconds,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(w) FROM WorkoutSession w WHERE w.user.id = :userId " +
            "AND w.status = :status AND w.durationSeconds >= :minimumDurationSeconds " +
            "AND EXISTS (SELECT 1 FROM SessionExercise se JOIN se.sets s " +
            "WHERE se.workoutSession = w AND s.reps > 0)")
    long countValidFinishedWorkoutsByUserId(@Param("userId") UUID userId,
            @Param("status") WorkoutStatus status,
            @Param("minimumDurationSeconds") int minimumDurationSeconds);

    @Query("SELECT COUNT(w) FROM WorkoutSession w WHERE w.finishedAt >= :twentyFourHoursAgo AND w.status = 'FINISHED'")
    Long countWorkoutsLast24Hours(@Param("twentyFourHoursAgo") LocalDateTime twentyFourHoursAgo);

}
