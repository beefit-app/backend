package com.beefit.api.dto.workout;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.beefit.api.domain.Card;
import jakarta.annotation.Nullable;

public record WorkoutSessionResponseDTO(
        UUID id,
        String status,
        LocalDateTime startedAt,
        @Nullable LocalDateTime finishedAt,
        @Nullable Integer durationSeconds,
        @Nullable Double estimatedCalories,
        @Nullable Double totalVolume,
        List<String> muscleGroups,
        Boolean isPrivate,
        List<SessionExerciseResponseDTO> exercises,
        Card card) {
}
