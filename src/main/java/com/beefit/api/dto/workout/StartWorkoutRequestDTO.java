package com.beefit.api.dto.workout;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record StartWorkoutRequestDTO(
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 50) String> muscleGroups,
        @NotNull Boolean isPrivate,
        @Nullable UUID templateId,
        @Valid @Size(max = 20) List<StartWorkoutExerciseItemDTO> exercises) {
}
