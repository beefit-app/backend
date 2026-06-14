package com.beefit.api.dto;

import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.enums.MuscleGroups;

import java.util.List;
import java.util.UUID;

public record WorkoutTemplateResponseDTO(
        UUID id,
        String name,
        List<MuscleGroups> muscleGroups,
        List<TemplateExercise> exercises
) {}
