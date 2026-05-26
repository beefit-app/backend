package com.beefit.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.beefit.api.domain.enums.MuscleGroups;

@Component
public class WorkoutMuscleGroupMapper {
    public List<MuscleGroups> toDomain(List<String> values) {
        return values.stream()
                .map(this::toDomain)
                .toList();
    }

    public MuscleGroups toDomain(String value) {
        return switch (value.trim().toLowerCase()) {
            case "peito" -> MuscleGroups.CHEST;
            case "costas" -> MuscleGroups.BACK;
            case "pernas" -> MuscleGroups.LEGS;
            case "ombros" -> MuscleGroups.SHOULDERS;
            case "bracos" -> MuscleGroups.ARMS;
            case "core" -> MuscleGroups.CORE;
            default -> throw new IllegalArgumentException("Grupo muscular inválido: " + value);
        };
    }

    public List<String> toApi(List<MuscleGroups> values) {
        return values.stream()
                .map(this::toApi)
                .toList();
    }

    public String toApi(MuscleGroups value) {
        return switch (value) {
            case CHEST -> "peito";
            case BACK -> "costas";
            case LEGS -> "pernas";
            case SHOULDERS -> "ombros";
            case ARMS -> "bracos";
            case CORE -> "core";
        };
    }
}
