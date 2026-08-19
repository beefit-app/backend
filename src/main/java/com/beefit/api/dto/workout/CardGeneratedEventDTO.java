package com.beefit.api.dto.workout;

import java.util.Map;
import java.util.UUID;

public record CardGeneratedEventDTO(
        UUID workoutId,
        UUID cardId,
        UUID exerciseId,
        String exerciseName,
        Double bestSetWeightKg,
        Integer bestSetReps,
        Double estimatedOneRepMax,
        Double improvementPercentage,
        String rarity,
        String muscleGroup,
        Map<String, Integer> muscleIntensities
) {}
