package com.beefit.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.beefit.api.domain.enums.MuscleGroups;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record FinishWorkoutSnapshotRequestDTO(
        @NotNull LocalDateTime finishedAt,
        @NotEmpty List<MuscleGroups> muscleGroups,
        @NotNull @Valid List<FinishWorkoutExerciseDTO> exercises) {
}
