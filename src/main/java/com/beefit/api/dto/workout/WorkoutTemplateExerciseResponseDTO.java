package com.beefit.api.dto.workout;

import java.util.UUID;

public record WorkoutTemplateExerciseResponseDTO(
        UUID catalogExerciseId,
        String catalogExerciseName,
        Integer order
) {}
