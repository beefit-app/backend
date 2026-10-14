package com.beefit.api.domain.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum AnatomicalMuscle {

    ABS(
            "abs",
            "abdominals",
            "abdominais",
            "transverse_abdominis"
    ),

    ADDUCTORS(
            "adductors",
            "adutores"
    ),

    BICEPS(
            "biceps"
    ),

    CALVES(
            "calves",
            "panturrilhas"
    ),

    CHEST(
            "chest",
            "peito",
            "peitoral"
    ),

    DELTOIDS(
            "deltoids",
            "shoulders",
            "ombros",
            "posteriores-de-ombro"
    ),

    FOREARM(
            "forearm",
            "forearms",
            "forearm_flexors",
            "forearm_extensors",
            "brachioradialis",
            "antebraço",
            "antebraco",
            "antebracos"
    ),

    GLUTEAL(
            "gluteal",
            "glutes",
            "gluteos",
            "abductors",
            "abdutores"
    ),

    HAMSTRING(
            "hamstring",
            "hamstrings",
            "isquiotibiais",
            "posteriores-de-coxa",
            "posterior"
    ),

    LOWER_BACK(
            "lower-back",
            "lower back",
            "inferior-das-costas"
    ),

    NECK(
            "neck",
            "pescoco"
    ),

    QUADRICEPS(
            "quadriceps"
    ),

    RHOMBOIDS(
            "rhomboids",
            "romboides"
    ),

    SERRATUS(
            "serratus_anterior",
            "serratus anterior",
            "serrátil anterior"
    ),

    TRAPEZIUS(
            "trapezius",
            "traps",
            "trapezio"
    ),

    TRICEPS(
            "triceps"
    ),

    UPPER_BACK(
            "upper-back",
            "lats",
            "middle back",
            "dorsais",
            "dorsal",
            "meio-das-costas"
    );

    private final String slug;
    private final List<String> seedValues;

    AnatomicalMuscle(String slug, String... seedValues) {
        this.slug = slug;
        this.seedValues = Arrays.asList(seedValues);
    }

    public static AnatomicalMuscle fromSeedValue(String value) {

        String normalizedValue = value
                .toLowerCase()
                .trim();

        return Arrays.stream(values())
                .filter(muscle ->
                        muscle.slug.equals(normalizedValue)
                                || muscle.seedValues.contains(normalizedValue)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Músculo anatômico não suportado: " + value
                        )
                );
    }
}