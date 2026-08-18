package com.beefit.api.repository;

import com.beefit.api.domain.SessionExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionExerciseRepository extends JpaRepository<SessionExercise, UUID> {

    // Busca todos os exercícios de uma sessão específica ordenados pela coluna 'exercise_order'
    List<SessionExercise> findByWorkoutSessionOrderByOrderAsc(UUID workoutId);
}
