package com.beefit.api.domain.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum MuscleGroups {

    CHEST(List.of(
            "chest",
            "peito",
            "peitoral",
            "serratus_anterior",
            "serratus anterior",
            "serrátil anterior"
    )),

    BACK(List.of(
            "back",
            "costas",
            "lats",
            "middle back",
            "meio-das-costas",
            "dorsal",
            "dorsais",
            "lower back",
            "inferior-das-costas",
            "rhomboids",
            "romboides"
    )),

    LEGS(List.of(
            "legs",
            "perna",
            "quadriceps",
            "hamstrings",
            "calves",
            "adductors",
            "abductors",
            "posteriores-de-coxa",
            "posterior",
            "isquiotibiais",
            "panturrilhas",
            "adutores",
            "abdutores"
    )),

    GLUTES(List.of(
            "glutes",
            "gluteos"
    )),

    SHOULDERS(List.of(
            "shoulders",
            "ombros",
            "posteriores-de-ombro"
    )),

    ARMS(List.of(
            "arms",
            "bracos",
            "biceps",
            "triceps",
            "forearms",
            "forearm_flexors",
            "forearm_extensors",
            "brachioradialis",
            "antebraco",
            "antebraco",
            "antebracos"
    )),

    CORE(List.of(
            "core",
            "abdominals",
            "abdomen",
            "abdominais",
            "transverse_abdominis"
    )),

    TRAPS(List.of(
            "traps",
            "trapezius",
            "trapezio",
            "pescoco"
    ));

    private final List<String> muscleSynonyms;

    MuscleGroups(List<String> muscleSynonyms) {
        this.muscleSynonyms = muscleSynonyms;
    }

    public static MuscleGroups fromString(String value) {

        String cleanValue = value
                .toLowerCase()
                .trim();

        for (MuscleGroups group : MuscleGroups.values()) {

            if (group.getMuscleSynonyms().contains(cleanValue)) {
                return group;
            }
        }

        throw new IllegalArgumentException(
                "Grupo muscular não suportado: " + value
        );
    }
}