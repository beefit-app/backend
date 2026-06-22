package com.beefit.api.dto.workout;

import java.time.LocalDateTime;
import java.util.List;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record FinishWorkoutSnapshotRequestDTO(
        @NotNull LocalDateTime finishedAt,
        @NotEmpty List<String> muscleGroups,
        @NotNull @Valid List<FinishWorkoutExerciseDTO> exercises,
        String title,
        Boolean shouldPostWorkout,
        java.util.UUID photoMediaId) {
}
