package com.beefit.api.service;
import com.beefit.api.domain.Card;
import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.AnatomicalMuscle;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.repository.CardRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CardService {

    private static final double PRIMARY_MUSCLE_WEIGHT = 1.0;
    private static final double SECONDARY_MUSCLE_WEIGHT = 0.35;
    private static final int MAX_HEATMAP_INTENSITY = 4;

    private final InventoryRepository inventoryRepository;
    private final CardRepository cardRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    public Card generateCard(User user, WorkoutSession workout){

        Double bestVolume = workoutSessionRepository.findMaxTotalVolumeByUserId(user.getId(), WorkoutStatus.FINISHED).orElse(0.0);

        boolean personalRecord = workout.getTotalVolume() > bestVolume;

        Inventory inventory = inventoryRepository.findByUserIdWithCards(user.getId())
                .orElseGet(() -> createInventory(user));

        double percentualPerformance = calculatePercentualPerformance(user, workout);

        Card card = new Card();

        double attackPower = calculatePeakAttackPower(workout);
        CardRarity selectedCardRarity = selectCardRarity(percentualPerformance);

        card.setAttackPower(attackPower);
        card.setBodyWeightAtGeneration(Double.valueOf(user.getBodyWeight()));
        card.setIsPersonalRecord(personalRecord);
        card.setWorkoutSession(workout);
        card.setCardRarity(selectedCardRarity);
        MuscleGroups cardMuscleGroup = resolveCardMuscleGroup(workout);
        card.setMuscleGroup(cardMuscleGroup);
        card.setMuscleIntensities(calculateMuscleIntensities(workout, cardMuscleGroup));

        inventory.addCard(card);
        user.setCardsCount((user.getCardsCount() == null ? 0 : user.getCardsCount()) + 1);

        return cardRepository.save(card);
    }

    private Inventory createInventory(User user) {
        Inventory inventory = new Inventory();
        inventory.setUser(user);
        inventory.setTotalCards(0);
        return inventoryRepository.save(inventory);
    }

    private double calculatePercentualPerformance(User user, WorkoutSession workout){
        List<WorkoutSession> lastWorkouts = workoutSessionRepository.
                findTop10ByUserIdAndStatusOrderByFinishedAtDesc(user.getId(), WorkoutStatus.FINISHED);

        Double averageVolume = lastWorkouts.stream().
                mapToDouble(WorkoutSession::getTotalVolume).
                average().orElse(workout.getTotalVolume());

        return workout.getTotalVolume()/averageVolume;
    }

    private double calculatePeakAttackPower(WorkoutSession workout) {
        return workout.getExercises().stream()
                .flatMap(exercise -> exercise.getSets().stream())
                .mapToDouble(set -> {
                    if (set.getWeightKg() <= 0 || set.getReps() <= 0) {
                        return 0.0;
                    }

                    int repsForEpley = Math.min(set.getReps(), 10);

                    return set.getWeightKg() * (1.0 + (repsForEpley / 30.0));
                })
                .max()
                .orElse(0.0);
    }

    private CardRarity selectCardRarity(double percentualPerformance){
        int randomNumber = ThreadLocalRandom.current().nextInt(100);

        if(percentualPerformance<0.9){
            if(randomNumber<80) return CardRarity.COMMON;
            if(randomNumber<95) return CardRarity.RARE;
            return CardRarity.EPIC;
        }
        if(percentualPerformance<1.05){
            if(randomNumber<40) return CardRarity.COMMON;
            if(randomNumber<80) return CardRarity.RARE;
            if(randomNumber<95) return CardRarity.EPIC;
            return CardRarity.LEGENDARY;
        }
        if(percentualPerformance<1.20){
            if(randomNumber<20) return CardRarity.COMMON;
            if(randomNumber<65) return CardRarity.RARE;
            if(randomNumber<93) return CardRarity.EPIC;
            return CardRarity.LEGENDARY;
        }
        if(randomNumber<5) return CardRarity.COMMON;
        if(randomNumber<40) return CardRarity.RARE;
        if(randomNumber<85) return CardRarity.EPIC;
        return CardRarity.LEGENDARY;
    }

    private MuscleGroups resolveCardMuscleGroup(WorkoutSession workout) {
        if (workout.getMuscleGroupsList() != null && !workout.getMuscleGroupsList().isEmpty()) {
            return workout.getMuscleGroupsList().getFirst();
        }

        return MuscleGroups.CORE;
    }

    Map<String, Integer> calculateMuscleIntensities(WorkoutSession workout, MuscleGroups fallbackMuscleGroup) {
        Map<AnatomicalMuscle, Double> scores = new LinkedHashMap<>();

        if (workout.getExercises() != null) {
            for (SessionExercise sessionExercise : workout.getExercises()) {
                CatalogExercise catalogExercise = sessionExercise.getExercise();
                if (catalogExercise == null) continue;

                long completedSets = sessionExercise.getSets() == null
                        ? 0
                        : sessionExercise.getSets().stream().filter(set -> set.getReps() != null && set.getReps() > 0).count();
                if (completedSets == 0) continue;

                Set<AnatomicalMuscle> primaryMuscles = distinctMuscles(catalogExercise.getPrimaryMusclesList());
                Set<AnatomicalMuscle> secondaryMuscles = distinctMuscles(catalogExercise.getSecondaryMusclesList());
                secondaryMuscles.removeAll(primaryMuscles);

                addScores(scores, primaryMuscles, completedSets * PRIMARY_MUSCLE_WEIGHT);
                addScores(scores, secondaryMuscles, completedSets * SECONDARY_MUSCLE_WEIGHT);
            }
        }

        if (scores.isEmpty()) {
            return fallbackIntensities(fallbackMuscleGroup);
        }

        double highestScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        Map<String, Integer> intensities = new LinkedHashMap<>();
        scores.forEach((muscle, score) -> {
            int intensity = Math.max(1, Math.min(
                    MAX_HEATMAP_INTENSITY,
                    (int) Math.ceil((score / highestScore) * MAX_HEATMAP_INTENSITY)));
            intensities.put(muscle.getSlug(), intensity);
        });
        return intensities;
    }

    private Set<AnatomicalMuscle> distinctMuscles(List<AnatomicalMuscle> muscles) {
        return muscles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(muscles);
    }

    private void addScores(
            Map<AnatomicalMuscle, Double> scores,
            Set<AnatomicalMuscle> muscles,
            double contribution) {
        muscles.forEach(muscle -> scores.merge(muscle, contribution, Double::sum));
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
        muscles.forEach(muscle -> intensities.put(muscle.getSlug(), MAX_HEATMAP_INTENSITY));
        return intensities;
    }
}
