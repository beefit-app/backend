package com.beefit.api.service;
import com.beefit.api.domain.Card;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.repository.CardRepository;
import com.beefit.api.repository.InventoryRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CardService {

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
        card.setMuscleGroup(resolveCardMuscleGroup(workout));

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
}
