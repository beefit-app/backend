package com.beefit.api.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.Card;
import com.beefit.api.dto.CardFilterDTO;
import com.beefit.api.dto.InventoryCardResponseDTO;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.repository.InventoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final WorkoutMuscleGroupMapper workoutMuscleGroupMapper;

    @Transactional(readOnly = true)
    public Page<InventoryCardResponseDTO> getUserCards(UUID userId, CardFilterDTO filters, Pageable pageable) {
        return inventoryRepository.findCardsWithFilters(userId, filters.muscleGroup(), filters.rarity(), pageable)
                .map(this::toResponse);
    }

    private InventoryCardResponseDTO toResponse(Card card) {
        return new InventoryCardResponseDTO(
                card.getId(),
                card.getWorkoutSession().getId(),
                card.getExercise().getId(),
                card.getExercise().getName(),
                card.getBestSetWeightKg(),
                card.getBestSetReps(),
                card.getEstimatedOneRepMax(),
                card.getImprovementPercentage(),
                card.getCardRarity().name(),
                card.getCardRarity().getRarity(),
                workoutMuscleGroupMapper.toApi(card.getMuscleGroup()),
                card.getMuscleIntensities(),
                card.getBodyWeightAtGeneration(),
                card.getGeneratedAt());
    }
}
