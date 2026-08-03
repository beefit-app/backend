package com.beefit.api.domain.enums;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum AnatomicalMuscle {
    ABS("abs", "abdominais"),
    ADDUCTORS("adductors", "adutores"),
    BICEPS("biceps", "biceps"),
    CALVES("calves", "panturrilhas"),
    CHEST("chest", "peito"),
    DELTOIDS("deltoids", "ombros"),
    FOREARM("forearm", "antebracos"),
    GLUTEAL("gluteal", "gluteos", "abdutores"),
    HAMSTRING("hamstring", "isquiotibiais"),
    LOWER_BACK("lower-back", "inferior-das-costas"),
    NECK("neck", "pescoco"),
    QUADRICEPS("quadriceps", "quadriceps"),
    TRAPEZIUS("trapezius", "trapezio"),
    TRICEPS("triceps", "triceps"),
    UPPER_BACK("upper-back", "dorsais", "meio-das-costas");

    private final String slug;
    private final java.util.List<String> seedValues;

    AnatomicalMuscle(String slug, String... seedValues) {
        this.slug = slug;
        this.seedValues = Arrays.asList(seedValues);
    }

    public static AnatomicalMuscle fromSeedValue(String value) {
        String normalizedValue = value.toLowerCase().trim();
        return Arrays.stream(values())
                .filter(muscle -> muscle.seedValues.contains(normalizedValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Músculo anatômico não suportado: " + value));
    }
}
