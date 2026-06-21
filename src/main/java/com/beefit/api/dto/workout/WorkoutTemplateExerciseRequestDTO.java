package com.beefit.api.dto.workout;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record WorkoutTemplateExerciseRequestDTO(
        @NotNull UUID catalogExerciseId,
        @NotNull @Positive Integer order
) {}
