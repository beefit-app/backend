package com.beefit.api.dto.workout;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record FinishWorkoutSnapshotRequestDTO(
        @NotBlank String finishedAt,
        @NotEmpty List<String> muscleGroups,
        @NotNull @Valid List<FinishWorkoutExerciseDTO> exercises,
        String title,
        Boolean shouldPostWorkout,
        java.util.UUID photoMediaId) {
}
