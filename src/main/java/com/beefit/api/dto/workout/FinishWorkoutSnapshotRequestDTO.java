package com.beefit.api.dto.workout;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinishWorkoutSnapshotRequestDTO(
        @NotBlank @Size(max = 64) String finishedAt,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 50) String> muscleGroups,
        @NotNull @Valid @Size(max = 20) List<FinishWorkoutExerciseDTO> exercises,
        @Size(max = 80) String title,
        Boolean shouldPostWorkout,
        java.util.UUID photoMediaId) {
}
