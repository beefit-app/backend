package com.beefit.api.dto.workout;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import jakarta.validation.constraints.Size;

public record FinishWorkoutExerciseDTO(
        @Nullable UUID sessionExerciseId,
        @NotBlank @Size(max = 64) String catalogExerciseId,
        @Nullable @Size(max = 255) String catalogExerciseName,
        @NotNull @Positive Integer order,
        @NotNull @Valid @Size(max = 20) List<FinishWorkoutSetDTO> sets) {
}
