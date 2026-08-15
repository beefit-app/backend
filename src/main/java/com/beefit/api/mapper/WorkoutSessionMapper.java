package com.beefit.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.Card;
import com.beefit.api.dto.workout.WorkoutCardResponseDTO;
import com.beefit.api.dto.workout.WorkoutSessionResponseDTO;

@Mapper(componentModel = "spring", uses = { WorkoutMuscleGroupMapper.class, SessionExerciseMapper.class })
public interface WorkoutSessionMapper {

    @Mapping(target = "status", expression = "java(workout.getStatus().getStatus())")
    @Mapping(source = "muscleGroupsList", target = "muscleGroups")
    @Mapping(source = "exercises", target = "exercises")
    @Mapping(target = "card", expression = "java(toCardDto(workout.getCard()))")
    WorkoutSessionResponseDTO toDto(WorkoutSession workout);

    default WorkoutCardResponseDTO toCardDto(Card card) {
        if (card == null) return null;
        return new WorkoutCardResponseDTO(
                card.getId(),
                card.getExercise().getId(),
                card.getExercise().getName(),
                card.getBestSetWeightKg(),
                card.getBestSetReps(),
                card.getEstimatedOneRepMax(),
                card.getImprovementPercentage(),
                card.getCardRarity().name(),
                card.getMuscleGroup().name(),
                card.getMuscleIntensities());
    }

}
