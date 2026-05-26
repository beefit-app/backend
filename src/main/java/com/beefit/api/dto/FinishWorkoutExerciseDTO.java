package com.beefit.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FinishWorkoutExerciseDTO(
        @Nullable UUID sessionExerciseId,
        @NotNull UUID catalogExerciseId,
        @NotNull @Positive Integer order,
        @NotNull @Valid List<FinishWorkoutSetDTO> sets) {
}
