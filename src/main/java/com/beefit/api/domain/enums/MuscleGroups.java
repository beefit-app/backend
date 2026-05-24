package com.beefit.api.domain.enums;

import lombok.Getter;

@Getter
public enum MuscleGroups {
    CHEST("chest"),
    BACK("back"),
    LEGS("legs"),
    SHOULDERS("shoulders"),
    ARMS("arms"),
    CORE("core");

    final String muscle;

    MuscleGroups(String muscle){
        this.muscle = muscle;
    }
}
