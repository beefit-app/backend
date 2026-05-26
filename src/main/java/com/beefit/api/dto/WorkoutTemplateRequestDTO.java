package com.beefit.api.dto;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.enums.MuscleGroups;

import java.util.List;

public record WorkoutTemplateRequestDTO (
    String name,
    List<MuscleGroups> muscleGroups,
    List<CatalogExercise> exercises
) {}
