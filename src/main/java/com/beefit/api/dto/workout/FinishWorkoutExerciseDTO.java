package com.beefit.api.dto.workout;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record FinishWorkoutExerciseDTO(
        @Nullable UUID sessionExerciseId,
        @NotBlank String catalogExerciseId,
        @Nullable String catalogExerciseName,
        @NotNull @Positive Integer order,
        @NotNull @Valid List<FinishWorkoutSetDTO> sets) {
}
