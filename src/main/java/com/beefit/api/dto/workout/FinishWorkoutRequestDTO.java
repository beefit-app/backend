package com.beefit.api.dto.workout;

import java.time.LocalDateTime;

import jakarta.annotation.Nullable;

public record FinishWorkoutRequestDTO(
        @Nullable LocalDateTime finishedAt) {
}
