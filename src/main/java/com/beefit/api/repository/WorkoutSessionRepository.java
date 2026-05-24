package com.beefit.api.repository;

import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.WorkoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

    // O Spring DATA JPA consegue fazer uma análise do nome da função e montar a query sozinho
    // Ele faz o caminho: WorkoutSession -> User -> id e monta a query:
    // SELECT * FROM workout_sessions WHERE user_id = {Valor passado}
    // Pesquisem por "JPA query methods" na documentação do Spring Data JPA
    List<WorkoutSession> findAllByUserId(UUID id);

    // Esse and é reservado e é traduzido na query também
    // Esse método é capaz de selecionar só os treinos ativos ou finalizados de um certo usuário
    List<WorkoutSession> findByStatusAndUserId(WorkoutStatus status, UUID id);
}
