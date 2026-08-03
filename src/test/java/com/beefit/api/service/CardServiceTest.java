package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.AnatomicalMuscle;
import com.beefit.api.repository.CardRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @InjectMocks
    private CardService cardService;

    @Test
    void calculatesPrimaryAndSecondaryMuscleIntensityFromCompletedSets() {
        CatalogExercise inclineBenchPress = new CatalogExercise();
        inclineBenchPress.setPrimaryMuscleGroupsList(List.of(MuscleGroups.CHEST));
        inclineBenchPress.setSecondaryMuscleGroupsList(List.of(MuscleGroups.SHOULDERS, MuscleGroups.ARMS));
        inclineBenchPress.setPrimaryMusclesList(List.of(AnatomicalMuscle.CHEST));
        inclineBenchPress.setSecondaryMusclesList(List.of(AnatomicalMuscle.DELTOIDS, AnatomicalMuscle.TRICEPS));

        SessionExercise sessionExercise = new SessionExercise();
        sessionExercise.setExercise(inclineBenchPress);
        sessionExercise.setSets(List.of(completedSet(), completedSet(), completedSet(), completedSet()));

        WorkoutSession workout = new WorkoutSession();
        workout.setExercises(List.of(sessionExercise));

        Map<String, Integer> intensities = cardService.calculateMuscleIntensities(workout, MuscleGroups.CHEST);

        assertEquals(4, intensities.get("chest"));
        assertEquals(2, intensities.get("deltoids"));
        assertEquals(2, intensities.get("triceps"));
        assertEquals(null, intensities.get("biceps"));
        assertEquals(null, intensities.get("forearm"));
    }

    @Test
    void accumulatesContributionsAndIgnoresSetsWithoutRepetitions() {
        CatalogExercise chestExercise = new CatalogExercise();
        chestExercise.setPrimaryMuscleGroupsList(List.of(MuscleGroups.CHEST));
        chestExercise.setSecondaryMuscleGroupsList(List.of(MuscleGroups.ARMS));
        chestExercise.setPrimaryMusclesList(List.of(AnatomicalMuscle.CHEST));
        chestExercise.setSecondaryMusclesList(List.of(AnatomicalMuscle.TRICEPS));

        SessionExercise firstExercise = new SessionExercise();
        firstExercise.setExercise(chestExercise);
        firstExercise.setSets(List.of(completedSet(), completedSet(), emptySet()));

        CatalogExercise armExercise = new CatalogExercise();
        armExercise.setPrimaryMuscleGroupsList(List.of(MuscleGroups.ARMS));
        armExercise.setSecondaryMuscleGroupsList(List.of());
        armExercise.setPrimaryMusclesList(List.of(AnatomicalMuscle.TRICEPS));
        armExercise.setSecondaryMusclesList(List.of());

        SessionExercise secondExercise = new SessionExercise();
        secondExercise.setExercise(armExercise);
        secondExercise.setSets(List.of(completedSet(), completedSet()));

        WorkoutSession workout = new WorkoutSession();
        workout.setExercises(List.of(firstExercise, secondExercise));

        Map<String, Integer> intensities = cardService.calculateMuscleIntensities(workout, MuscleGroups.CHEST);

        assertEquals(3, intensities.get("chest"));
        assertEquals(4, intensities.get("triceps"));
    }

    @Test
    void fallsBackToCardMuscleGroupWhenCatalogHasNoUsableData() {
        WorkoutSession workout = new WorkoutSession();

        assertEquals(
                Map.of("abs", 4),
                cardService.calculateMuscleIntensities(workout, MuscleGroups.CORE));
    }

    private com.beefit.api.domain.Set completedSet() {
        com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
        set.setReps(10);
        return set;
    }

    private com.beefit.api.domain.Set emptySet() {
        com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
        set.setReps(0);
        return set;
    }
}
