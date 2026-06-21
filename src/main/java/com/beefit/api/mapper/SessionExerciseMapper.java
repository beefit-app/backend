package com.beefit.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.beefit.api.domain.SessionExercise;
import com.beefit.api.dto.workout.SessionExerciseResponseDTO;

@Mapper(componentModel = "spring", uses = SetMapper.class)
public interface SessionExerciseMapper {

    @Mapping(source = "exercise.id", target = "catalogExerciseId")
    @Mapping(source = "exercise.name", target = "catalogExerciseName")
    SessionExerciseResponseDTO toDto(SessionExercise sessionExercise);
}
