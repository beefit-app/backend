package com.beefit.api.dto.workout;

import java.util.UUID;

public record WorkoutCardResponseDTO(
        UUID id,
        Double attackPower,
        String rarity,
        String muscleGroup,
        Boolean isPersonalRecord) {
}
