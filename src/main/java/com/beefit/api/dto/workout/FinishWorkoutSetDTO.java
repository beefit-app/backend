package com.beefit.api.dto.workout;

import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record FinishWorkoutSetDTO(
        @Nullable UUID id,
        @NotNull @Positive Integer order,
        @NotNull @PositiveOrZero Double weightKg,
        @NotNull @PositiveOrZero Integer reps) {
}
