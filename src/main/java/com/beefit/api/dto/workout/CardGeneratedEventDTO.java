package com.beefit.api.dto.workout;

import java.util.UUID;

public record CardGeneratedEventDTO(
        UUID workoutId,
        UUID cardId,
        Double attackPower,
        String rarity,
        String muscleGroup,
        Boolean isPersonalRecord
) {}
