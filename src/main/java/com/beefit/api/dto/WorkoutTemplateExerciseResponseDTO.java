package com.beefit.api.dto;

import java.util.UUID;

public record WorkoutTemplateExerciseResponseDTO(
        UUID catalogExerciseId,
        String catalogExerciseName,
        Integer order
) {}
