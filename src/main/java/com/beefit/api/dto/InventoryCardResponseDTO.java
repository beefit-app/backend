package com.beefit.api.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record InventoryCardResponseDTO(
        UUID id,
        UUID workoutSessionId,
        Double attackPower,
        String rarity,
        String rarityLabel,
        String muscleGroup,
        Map<String, Integer> muscleIntensities,
        Boolean isPersonalRecord,
        Double bodyWeightAtGeneration,
        LocalDateTime generatedAt
) {
}
