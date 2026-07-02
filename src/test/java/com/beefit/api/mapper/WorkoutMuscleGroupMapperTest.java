package com.beefit.api.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.beefit.api.domain.enums.MuscleGroups;

class WorkoutMuscleGroupMapperTest {

    private final WorkoutMuscleGroupMapper mapper = new WorkoutMuscleGroupMapper();

    @Test
    void toDomainMapsSupportedApiValues() {
        assertEquals(MuscleGroups.CHEST, mapper.toDomain("peito"));
        assertEquals(MuscleGroups.BACK, mapper.toDomain("costas"));
        assertEquals(MuscleGroups.LEGS, mapper.toDomain("pernas"));
        assertEquals(MuscleGroups.SHOULDERS, mapper.toDomain("ombros"));
        assertEquals(MuscleGroups.ARMS, mapper.toDomain("bracos"));
        assertEquals(MuscleGroups.CORE, mapper.toDomain("core"));
    }

    @Test
    void toDomainRejectsUnsupportedValue() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain("invalid"));
    }

    @Test
    void toApiMapsDomainValues() {
        assertEquals(
                List.of("peito", "costas", "pernas", "gluteos", "trapezio", "ombros", "bracos", "core"),
                mapper.toApi(List.of(
                        MuscleGroups.CHEST,
                        MuscleGroups.BACK,
                        MuscleGroups.LEGS,
                        MuscleGroups.GLUTES,
                        MuscleGroups.TRAPS,
                        MuscleGroups.SHOULDERS,
                        MuscleGroups.ARMS,
                        MuscleGroups.CORE)));
    }
}
