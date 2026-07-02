package com.beefit.api.dto.workout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkoutTemplateRequestDTO(
        @NotBlank(message = "O nome do treino não pode estar vazio.")
        String name,
        @NotEmpty(message = "A lista de grupos musculares não pode ser vazia.")
        List<String> muscleGroups,
        @NotEmpty(message = "A lista de exercícios não pode ser vazia.")
        @Valid
        List<WorkoutTemplateExerciseRequestDTO> exercises
) {}
