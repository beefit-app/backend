package com.beefit.api.domain.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum MuscleGroups {
    CHEST(List.of("chest", "peito", "peitoral")),
    BACK(List.of("back", "costas", "meio-das-costas", "dorsal", "dorsais", "inferior-das-costas", "panturrilhas")),
    LEGS(List.of("legs", "perna", "quadriceps", "posteriores-de-coxa", "posterior", "isquiotibiais", "adutores", "abdutores")),
    GLUTES(List.of("glutes", "gluteos")),
    SHOULDERS(List.of("shoulders", "ombros", "posteriores-de-ombro")),
    ARMS(List.of("arms", "bracos", "biceps", "triceps", "antebraco", "antebracos")),
    CORE(List.of("core", "abdomen", "abdominais")),
    TRAPS(List.of("traps", "trapezius", "trapezio", "pescoco"));

    private final List<String> muscleSynonyms;

    MuscleGroups(List<String> muscleSynonyms){
        this.muscleSynonyms = muscleSynonyms;
    }

    public static MuscleGroups fromString(String value) {

        String cleanValue = value.toLowerCase().trim();

        for (MuscleGroups group : MuscleGroups.values()) {
            if (group.getMuscleSynonyms().contains(cleanValue)) {
                return group;
            }
        }
        // Lança uma exceção clara se o JSON vier com um músculo que você não mapeou
        throw new IllegalArgumentException("Grupo muscular não suportado: " + value);
    }
}
