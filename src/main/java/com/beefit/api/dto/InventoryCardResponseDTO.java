package com.beefit.api.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record InventoryCardResponseDTO(
        UUID id,
        UUID workoutSessionId,
        Double attackPower,
        Boolean isPersonalRecord,
        UUID exerciseId,
        String exerciseName,
        Double bestSetWeightKg,
        Integer bestSetReps,
        Double estimatedOneRepMax,
        Double improvementPercentage,
        String rarity,
        String rarityLabel,
        String muscleGroup,
        Map<String, Integer> muscleIntensities,
        Double bodyWeightAtGeneration,
        LocalDateTime generatedAt
) {
}
