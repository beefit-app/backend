package com.beefit.api.mapper;

import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "Spring")
public interface WorkoutTemplateResponseMapper {

    @Mapping(source = "muscleGroupsList", target = "muscleGroups")
    @Mapping(source = "exerciseList", target = "exercises")
    @Mapping(source = "user.id", target = "userID")
    WorkoutTemplateResponseDTO toDto(WorkoutTemplate workoutTemplate);
}
