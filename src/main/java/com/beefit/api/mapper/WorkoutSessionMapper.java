package com.beefit.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.dto.workout.WorkoutSessionResponseDTO;

@Mapper(componentModel = "spring", uses = { WorkoutMuscleGroupMapper.class, SessionExerciseMapper.class })
public interface WorkoutSessionMapper {

    @Mapping(target = "status", expression = "java(workout.getStatus().getStatus())")
    @Mapping(source = "muscleGroupsList", target = "muscleGroups")
    @Mapping(source = "exercises", target = "exercises")
    WorkoutSessionResponseDTO toDto(WorkoutSession workout);

}
