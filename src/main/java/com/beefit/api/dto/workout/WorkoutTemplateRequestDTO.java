package com.beefit.api.dto.workout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkoutTemplateRequestDTO(
        @NotBlank(message = "O nome do treino não pode estar vazio.")
        @Size(max = 255, message = "O nome do treino deve ter no máximo 255 caracteres.")
        String name,
        @NotEmpty(message = "A lista de grupos musculares não pode ser vazia.")
        @Size(max = 8)
        List<@NotBlank @Size(max = 50) String> muscleGroups,
        @NotEmpty(message = "A lista de exercícios não pode ser vazia.")
        @Size(max = 20)
        @Valid
        List<WorkoutTemplateExerciseRequestDTO> exercises
) {}
