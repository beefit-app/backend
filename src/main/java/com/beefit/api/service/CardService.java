package com.beefit.api.service;

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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CardService {

    private static final double MINIMUM_CARD_IMPROVEMENT_PERCENTAGE = 3.0;
    private static final double RARE_IMPROVEMENT_PERCENTAGE = 7.5;
    private static final double EPIC_IMPROVEMENT_PERCENTAGE = 15.0;
    private static final double LEGENDARY_IMPROVEMENT_PERCENTAGE = 25.0;
    private static final int PRIMARY_MUSCLE_INTENSITY = 4;
    private static final int SECONDARY_MUSCLE_INTENSITY = 2;

    private final InventoryRepository inventoryRepository;
    private final CardRepository cardRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    @Nullable
    public Card generateCard(User user, WorkoutSession workout) {
        ExercisePerformance performance = selectExtraordinaryPerformance(user, workout).orElse(null);
        if (performance == null) return null;

        Inventory inventory = inventoryRepository.findByUserIdWithCards(user.getId())
                .orElseGet(() -> createInventory(user));

        Card card = new Card();
        card.setExercise(performance.exercise().getExercise());
        card.setBestSetWeightKg(performance.bestSet().getWeightKg());
        card.setBestSetReps(performance.bestSet().getReps());
        card.setEstimatedOneRepMax(performance.weighted() ? performance.score() : null);
        card.setImprovementPercentage(performance.improvementPercentage());
        card.setBodyWeightAtGeneration(Double.valueOf(user.getBodyWeight()));
        card.setWorkoutSession(workout);
        card.setCardRarity(selectCardRarity(performance.improvementPercentage()));
        card.setMuscleGroup(resolveCardMuscleGroup(performance.exercise(), workout));
        card.setMuscleIntensities(calculateMuscleIntensities(performance.exercise().getExercise()));

        inventory.addCard(card);
        user.setCardsCount((user.getCardsCount() == null ? 0 : user.getCardsCount()) + 1);
        return cardRepository.save(card);
    }

    private Optional<ExercisePerformance> selectExtraordinaryPerformance(User user, WorkoutSession workout) {
        if (workout.getExercises() == null) return Optional.empty();

        return workout.getExercises().stream()
                .map(exercise -> evaluateExercise(user, workout, exercise))
                .flatMap(Optional::stream)
                .filter(performance -> performance.improvementPercentage() >= MINIMUM_CARD_IMPROVEMENT_PERCENTAGE)
                .max(Comparator.comparingDouble(ExercisePerformance::improvementPercentage)
                        .thenComparingDouble(ExercisePerformance::score)
                        .thenComparing(performance -> -performance.exercise().getOrder()));
    }

    private Optional<ExercisePerformance> evaluateExercise(
            User user,
            WorkoutSession workout,
            SessionExercise exercise) {
        if (exercise.getExercise() == null || exercise.getSets() == null) return Optional.empty();

        List<com.beefit.api.domain.Set> completedSets = exercise.getSets().stream()
                .filter(set -> set.getReps() != null && set.getReps() > 0)
                .filter(set -> set.getWeightKg() != null && set.getWeightKg() >= 0)
                .toList();
        if (completedSets.isEmpty()) return Optional.empty();

        boolean weighted = completedSets.stream().anyMatch(set -> set.getWeightKg() > 0);
        Comparator<com.beefit.api.domain.Set> comparator = Comparator
                .comparingDouble(set -> performanceScore(set, weighted));
        com.beefit.api.domain.Set bestSet = completedSets.stream()
                .filter(set -> !weighted || set.getWeightKg() > 0)
                .max(comparator.thenComparing(com.beefit.api.domain.Set::getOrder))
                .orElseThrow();
        double score = performanceScore(bestSet, weighted);

        Optional<? extends Number> previousBest = weighted
                ? workoutSessionRepository.findBestEstimatedOneRepMaxBeforeWorkout(
                        user.getId(), exercise.getExercise().getId(), workout.getId())
                : workoutSessionRepository.findBestBodyweightRepsBeforeWorkout(
                        user.getId(), exercise.getExercise().getId(), workout.getId());
        if (previousBest.isEmpty() || previousBest.get().doubleValue() <= 0) return Optional.empty();

        double improvementPercentage = ((score - previousBest.get().doubleValue())
                / previousBest.get().doubleValue()) * 100.0;
        return Optional.of(new ExercisePerformance(
                exercise,
                bestSet,
                weighted,
                score,
                improvementPercentage));
    }

    private double performanceScore(com.beefit.api.domain.Set set, boolean weighted) {
        if (!weighted) return set.getReps();
        int repsForEpley = Math.min(set.getReps(), 10);
        return set.getWeightKg() * (1.0 + repsForEpley / 30.0);
    }

    private CardRarity selectCardRarity(double improvementPercentage) {
        if (improvementPercentage >= LEGENDARY_IMPROVEMENT_PERCENTAGE) return CardRarity.LEGENDARY;
        if (improvementPercentage >= EPIC_IMPROVEMENT_PERCENTAGE) return CardRarity.EPIC;
        if (improvementPercentage >= RARE_IMPROVEMENT_PERCENTAGE) return CardRarity.RARE;
        return CardRarity.COMMON;
    }

    private Inventory createInventory(User user) {
        Inventory inventory = new Inventory();
        inventory.setUser(user);
        inventory.setTotalCards(0);
        return inventoryRepository.save(inventory);
    }

    private MuscleGroups resolveCardMuscleGroup(SessionExercise exercise, WorkoutSession workout) {
        List<MuscleGroups> primaryGroups = exercise.getExercise().getPrimaryMuscleGroupsList();
        if (primaryGroups != null && !primaryGroups.isEmpty()) return primaryGroups.getFirst();
        if (workout.getMuscleGroupsList() != null && !workout.getMuscleGroupsList().isEmpty()) {
            return workout.getMuscleGroupsList().getFirst();
        }
        return MuscleGroups.CORE;
    }

    Map<String, Integer> calculateMuscleIntensities(CatalogExercise exercise) {
        Map<String, Integer> intensities = new LinkedHashMap<>();
        Set<AnatomicalMuscle> primaryMuscles = distinctMuscles(exercise.getPrimaryMusclesList());
        Set<AnatomicalMuscle> secondaryMuscles = distinctMuscles(exercise.getSecondaryMusclesList());
        secondaryMuscles.removeAll(primaryMuscles);

        primaryMuscles.forEach(muscle -> intensities.put(muscle.getSlug(), PRIMARY_MUSCLE_INTENSITY));
        secondaryMuscles.forEach(muscle -> intensities.put(muscle.getSlug(), SECONDARY_MUSCLE_INTENSITY));

        if (intensities.isEmpty()) {
            List<MuscleGroups> primaryGroups = exercise.getPrimaryMuscleGroupsList();
            MuscleGroups fallback = primaryGroups == null || primaryGroups.isEmpty()
                    ? MuscleGroups.CORE
                    : primaryGroups.getFirst();
            fallbackIntensities(fallback).forEach(intensities::put);
        }
        return intensities;
    }

    private Set<AnatomicalMuscle> distinctMuscles(List<AnatomicalMuscle> muscles) {
        return muscles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(muscles);
    }

    private Map<String, Integer> fallbackIntensities(MuscleGroups muscleGroup) {
        List<AnatomicalMuscle> muscles = switch (muscleGroup) {
            case CHEST -> List.of(AnatomicalMuscle.CHEST);
            case BACK -> List.of(AnatomicalMuscle.UPPER_BACK, AnatomicalMuscle.LOWER_BACK);
            case LEGS -> List.of(AnatomicalMuscle.QUADRICEPS, AnatomicalMuscle.HAMSTRING, AnatomicalMuscle.CALVES);
            case GLUTES -> List.of(AnatomicalMuscle.GLUTEAL);
            case SHOULDERS -> List.of(AnatomicalMuscle.DELTOIDS);
            case ARMS -> List.of(AnatomicalMuscle.BICEPS, AnatomicalMuscle.TRICEPS, AnatomicalMuscle.FOREARM);
            case CORE -> List.of(AnatomicalMuscle.ABS);
            case TRAPS -> List.of(AnatomicalMuscle.TRAPEZIUS);
        };
        Map<String, Integer> intensities = new LinkedHashMap<>();
        muscles.forEach(muscle -> intensities.put(muscle.getSlug(), PRIMARY_MUSCLE_INTENSITY));
        return intensities;
    }

    private record ExercisePerformance(
            SessionExercise exercise,
            com.beefit.api.domain.Set bestSet,
            boolean weighted,
            double score,
            double improvementPercentage) {
    }
}
