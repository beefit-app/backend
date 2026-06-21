package com.beefit.api.service;
import com.beefit.api.domain.Card;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.exception.workout.InventoryNotFoundException;
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
    private final WorkoutSessionRepository workoutSessionRepository;

    public Card generateCard(User user, WorkoutSession workout){

        Double bestVolume = workoutSessionRepository.findMaxTotalVolumeByUserId(user.getId(), WorkoutStatus.FINISHED).orElse(0.0);

        boolean personalRecord = workout.getTotalVolume() > bestVolume;

        Inventory inventory = inventoryRepository.findByUserIdWithCards(user.getId()).orElseThrow(InventoryNotFoundException::new);

        double percentualPerformance = calculatePercentualPerformance(user, workout);

        Card card = new Card();

        double attackPower = calculatePeakAttackPower(workout);
        CardRarity selectedCardRarity = selectCardRarity(percentualPerformance);

        card.setAttackPower(attackPower);
        card.setBodyWeightAtGeneration(Double.valueOf(user.getBodyWeight()));
        card.setIsPersonalRecord(personalRecord);
        card.setInventory(inventory);
        card.setWorkoutSession(workout);
        card.setCardRarity(selectedCardRarity);

        inventory.addCard(card);

        inventoryRepository.save(inventory);

        return card;
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
}
