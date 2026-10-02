package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.beefit.api.dto.workout.ExerciseBestScoreProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.Card;
import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.AnatomicalMuscle;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.repository.CardRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private CardRepository cardRepository;
    @Mock private WorkoutSessionRepository workoutSessionRepository;
    @InjectMocks private CardService cardService;

    private User user;
    private WorkoutSession workout;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setBodyWeight(80.0F);
        user.setCardsCount(0);

        workout = new WorkoutSession();
        workout.setId(UUID.randomUUID());
        workout.setMuscleGroupsList(List.of(MuscleGroups.CHEST));

        inventory = new Inventory();
        inventory.setUser(user);
        inventory.setTotalCards(0);
    }

    @Test
    void firstPerformanceOnlyEstablishesBaselineAndDoesNotGenerateCard() {
        SessionExercise benchPress = weightedExercise("Supino", 1, 100.0, 5);
        workout.setExercises(List.of(benchPress));
        when(workoutSessionRepository.findBestEstimatedOneRepMaxBatchBeforeWorkout(
                eq(user.getId()), any(), eq(workout.getId())))
                .thenReturn(List.of());

        assertNull(cardService.generateCard(user, workout));

        verify(cardRepository, never()).save(any());
        assertEquals(0, user.getCardsCount());
    }

    @Test
    void improvementBelowThreePercentDoesNotGenerateCard() {
        SessionExercise benchPress = weightedExercise("Supino", 1, 100.0, 1);
        workout.setExercises(List.of(benchPress));
        when(workoutSessionRepository.findBestEstimatedOneRepMaxBatchBeforeWorkout(
                eq(user.getId()), any(), eq(workout.getId())))
                .thenReturn(List.of(projection(benchPress.getExercise().getId(), 102.0)));

        assertNull(cardService.generateCard(user, workout));

        verify(inventoryRepository, never()).findByUserIdWithCards(any());
    }

    @Test
    void selectsExerciseWithLargestRelativeImprovementAndPersistsItsBestSet() {
        SessionExercise benchPress = weightedExercise("Supino", 1, 100.0, 5);
        SessionExercise row = weightedExercise("Remada", 2, 60.0, 1);
        workout.setExercises(List.of(benchPress, row));
        when(workoutSessionRepository.findBestEstimatedOneRepMaxBatchBeforeWorkout(
                eq(user.getId()), any(), eq(workout.getId())))
                .thenReturn(List.of(
                        projection(benchPress.getExercise().getId(), 100.0),
                        projection(row.getExercise().getId(), 50.0)
                ));
        prepareCardPersistence();

        Card card = cardService.generateCard(user, workout);

        assertSame(row.getExercise(), card.getExercise());
        assertEquals(60.0, card.getBestSetWeightKg());
        assertEquals(1, card.getBestSetReps());
        assertEquals(62.0, card.getEstimatedOneRepMax(), 0.001);
        assertEquals(24.0, card.getImprovementPercentage(), 0.001);
        assertEquals(CardRarity.EPIC, card.getCardRarity());
        assertEquals(1, user.getCardsCount());
    }

    @Test
    void bodyweightExerciseUsesRepetitionsAndCanGenerateRareCard() {
        SessionExercise pullUp = bodyweightExercise("Barra fixa", 1, 20);
        workout.setExercises(List.of(pullUp));
        when(workoutSessionRepository.findBestBodyweightRepsBatchBeforeWorkout(
                eq(user.getId()), any(), eq(workout.getId())))
                .thenReturn(List.of(projection(pullUp.getExercise().getId(), 18.0)));
        prepareCardPersistence();

        Card card = cardService.generateCard(user, workout);

        assertEquals(CardRarity.RARE, card.getCardRarity());
        assertEquals(0.0, card.getBestSetWeightKg());
        assertEquals(20, card.getBestSetReps());
        assertNull(card.getEstimatedOneRepMax());
        assertEquals(11.111, card.getImprovementPercentage(), 0.001);
    }

    @ParameterizedTest
    @CsvSource({
            "4.0, COMMON",
            "8.0, RARE",
            "16.0, EPIC",
            "26.0, LEGENDARY"
    })
    void assignsRarityFromImprovementPercentage(double improvement, CardRarity expectedRarity) {
        double targetOneRepMax = 100.0 * (1.0 + improvement / 100.0);
        double weightForOneRep = targetOneRepMax / (1.0 + 1.0 / 30.0);
        SessionExercise exercise = weightedExercise("Desenvolvimento", 1, weightForOneRep, 1);
        workout.setExercises(List.of(exercise));
        when(workoutSessionRepository.findBestEstimatedOneRepMaxBatchBeforeWorkout(
                eq(user.getId()), any(), eq(workout.getId())))
                .thenReturn(List.of(projection(exercise.getExercise().getId(), 100.0)));
        prepareCardPersistence();

        assertEquals(expectedRarity, cardService.generateCard(user, workout).getCardRarity());
    }

    @Test
    void muscleMapContainsOnlySelectedExerciseMuscles() {
        CatalogExercise exercise = catalogExercise("Supino inclinado");
        exercise.setPrimaryMusclesList(List.of(AnatomicalMuscle.CHEST));
        exercise.setSecondaryMusclesList(List.of(AnatomicalMuscle.DELTOIDS, AnatomicalMuscle.TRICEPS));

        assertEquals(
                Map.of("chest", 4, "deltoids", 2, "triceps", 2),
                cardService.calculateMuscleIntensities(exercise));
    }

    private void prepareCardPersistence() {
        when(inventoryRepository.findByUserIdWithCards(user.getId())).thenReturn(Optional.of(inventory));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ExerciseBestScoreProjection projection(UUID exerciseId, Double score) {
        return new ExerciseBestScoreProjection() {
            @Override
            public UUID getExerciseId() {
                return exerciseId;
            }

            @Override
            public Double getBestScore() {
                return score;
            }
        };
    }

    private SessionExercise weightedExercise(String name, int order, double weight, int reps) {
        return sessionExercise(name, order, weight, reps);
    }

    private SessionExercise bodyweightExercise(String name, int order, int reps) {
        return sessionExercise(name, order, 0.0, reps);
    }

    private SessionExercise sessionExercise(String name, int order, double weight, int reps) {
        SessionExercise sessionExercise = new SessionExercise();
        sessionExercise.setExercise(catalogExercise(name));
        sessionExercise.setOrder(order);

        com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
        set.setWeightKg(weight);
        set.setReps(reps);
        set.setOrder(1);
        sessionExercise.setSets(List.of(set));
        return sessionExercise;
    }

    private CatalogExercise catalogExercise(String name) {
        CatalogExercise exercise = new CatalogExercise();
        exercise.setId(UUID.randomUUID());
        exercise.setName(name);
        exercise.setPrimaryMuscleGroupsList(List.of(MuscleGroups.CHEST));
        exercise.setSecondaryMuscleGroupsList(List.of());
        exercise.setPrimaryMusclesList(List.of(AnatomicalMuscle.CHEST));
        exercise.setSecondaryMusclesList(List.of());
        return exercise;
    }
}
