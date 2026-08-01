package com.beefit.api.repository;

import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.WorkoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

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
      AND w.user.id <> :currentUserId
    ORDER BY w.startedAt DESC
    """)
    List<WorkoutSession> findAllPublicByStatusExcludingUser(
            @Param("status") WorkoutStatus status,
            @Param("currentUserId") UUID currentUserId
    );

    @Query("""
    SELECT MAX(w.totalVolume) FROM WorkoutSession w WHERE w.user.id = :userId AND w.status = :status
    """)
    Optional<Double> findMaxTotalVolumeByUserId(@Param("userId") UUID userId, @Param("status") WorkoutStatus status);

    List<WorkoutSession> findTop10ByUserIdAndStatusOrderByFinishedAtDesc(UUID userId, WorkoutStatus status);

     // [RF-011] Validação de Dia Civil para a Ofensiva (Honey)
     // Esta query conta se o usuário já possui algum treino finalizado no intervalo de tempo fornecido.
     // Será utilizada para garantir que o usuário só ganhe +1 Honey no primeiro treino do dia.

    @Query("SELECT COUNT(w) > 0 FROM WorkoutSession w WHERE w.user.id = :userId " +
            "AND w.status = :status AND w.finishedAt >= :startOfDay AND w.finishedAt <= :endOfDay")
    boolean existsFinishedWorkoutToday(
            @Param("userId") UUID userId,
            @Param("status") WorkoutStatus status,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Query("SELECT DISTINCT w.finishedAt FROM WorkoutSession w WHERE w.user.id = :userId " +
            "AND w.status = :status AND w.finishedAt >= :start AND w.finishedAt < :end")
    List<LocalDateTime> findFinishedDaysByUserAndMonth(@Param("userId") UUID userId, @Param("status") WorkoutStatus status,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(w) FROM WorkoutSession w WHERE w.finishedAt >= :twentyFourHoursAgo AND w.status = 'FINISHED'")
    Long countWorkoutsLast24Hours(@Param("twentyFourHoursAgo") LocalDateTime twentyFourHoursAgo);

}
