package com.beefit.api.dto;

import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;

public record CardFilterDTO(
        CardRarity rarity,
        MuscleGroups muscleGroup
) {
}
