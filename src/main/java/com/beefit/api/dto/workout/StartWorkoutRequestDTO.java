package com.beefit.api.dto.workout;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record StartWorkoutRequestDTO(
        @NotEmpty List<String> muscleGroups,
        @NotNull Boolean isPrivate,
        @Nullable UUID templateId,
        @Valid List<StartWorkoutExerciseItemDTO> exercises) {
}
