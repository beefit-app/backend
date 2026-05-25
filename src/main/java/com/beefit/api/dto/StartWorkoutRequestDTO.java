package com.beefit.api.dto;

import java.util.List;
import java.util.UUID;

import com.beefit.api.domain.enums.MuscleGroups;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record StartWorkoutRequestDTO(
        @NotEmpty List<MuscleGroups> muscleGroups,
        @NotNull Boolean isPrivate,
        @Nullable UUID templateId,
        @Valid List<StartWorkoutExerciseItemDTO> exercises) {
}
