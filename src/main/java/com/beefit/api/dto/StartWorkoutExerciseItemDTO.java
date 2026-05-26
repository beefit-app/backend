package com.beefit.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StartWorkoutExerciseItemDTO(
        @NotNull UUID catalogExerciseId,
        @NotNull @Positive Integer order) {
}
