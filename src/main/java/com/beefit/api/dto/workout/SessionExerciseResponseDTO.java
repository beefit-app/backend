package com.beefit.api.dto.workout;

import java.util.List;
import java.util.UUID;

public record SessionExerciseResponseDTO(
        UUID id,
        UUID catalogExerciseId,
        String catalogExerciseName,
        Integer order,
        List<SetResponseDTO> sets) {
}
