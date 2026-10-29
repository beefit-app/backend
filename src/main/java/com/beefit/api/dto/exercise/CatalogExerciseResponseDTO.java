package com.beefit.api.dto.exercise;

import com.beefit.api.domain.enums.AnatomicalMuscle;
import com.beefit.api.domain.enums.MuscleGroups;
import java.util.List;
import java.util.UUID;

public record CatalogExerciseResponseDTO(
        UUID id,
        String name,
        List<MuscleGroups> primaryMuscleGroupsList,
        List<MuscleGroups> secondaryMuscleGroupsList,
        List<AnatomicalMuscle> primaryMusclesList,
        List<AnatomicalMuscle> secondaryMusclesList,
        String instructions,
        List<String> images){
}
