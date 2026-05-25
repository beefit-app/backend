package com.beefit.api.dto;

import java.time.LocalDateTime;

import jakarta.annotation.Nullable;

public record FinishWorkoutRequestDTO(
        @Nullable LocalDateTime finishedAt) {
}
