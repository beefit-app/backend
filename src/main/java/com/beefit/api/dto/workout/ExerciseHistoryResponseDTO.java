package com.beefit.api.dto.workout;

import java.time.LocalDateTime;

import jakarta.annotation.Nullable;

public record ExerciseHistoryResponseDTO(
        @Nullable LocalDateTime lastPerformedAt,
        @Nullable Double bestWeightKg,
        @Nullable Integer bestReps,
        @Nullable Double lastWeightKg,
        @Nullable Integer lastReps) {
}
