package com.beefit.api.dto;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.enums.MuscleGroups;

import java.util.List;
import java.util.UUID;

public record WorkoutTemplateResponseDTO(
        UUID userID,
        String name,
        List<MuscleGroups> muscleGroups,
        List<CatalogExercise> exercises
) {}
