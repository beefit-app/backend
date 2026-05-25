package com.beefit.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.dto.WorkoutSessionResponseDTO;

@Mapper(componentModel = "spring", uses = WorkoutMuscleGroupMapper.class)
public interface WorkoutSessionMapper {

    @Mapping(target = "status", expression = "java(workout.getStatus().getStatus())")
    @Mapping(source = "muscleGroupsList", target = "muscleGroups")
    WorkoutSessionResponseDTO toDto(WorkoutSession workout);

}
