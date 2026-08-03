package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.beefit.api.domain.Card;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.dto.CardFilterDTO;
import com.beefit.api.dto.InventoryCardResponseDTO;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.repository.InventoryRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WorkoutMuscleGroupMapper workoutMuscleGroupMapper;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void getUserCardsDelegatesFiltersAndMapsPersistedCardFields() {
        UUID userId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID workoutSessionId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        CardFilterDTO filters = new CardFilterDTO(CardRarity.LEGENDARY, MuscleGroups.CHEST);

        User user = new User();
        user.setId(userId);
        user.setBodyWeight(95.0f);

        Inventory inventory = new Inventory();
        inventory.setUser(user);

        WorkoutSession workoutSession = new WorkoutSession();
        workoutSession.setId(workoutSessionId);

        Card card = new Card();
        card.setId(cardId);
        card.setInventory(inventory);
        card.setWorkoutSession(workoutSession);
        card.setAttackPower(120.0);
        card.setCardRarity(CardRarity.LEGENDARY);
        card.setMuscleGroup(MuscleGroups.CHEST);
        card.setMuscleIntensities(Map.of("chest", 4, "deltoids", 2, "triceps", 2));
        card.setIsPersonalRecord(Boolean.TRUE);
        card.setBodyWeightAtGeneration(80.0);
        card.setGeneratedAt(LocalDateTime.of(2026, 6, 18, 10, 30));

        when(inventoryRepository.findCardsWithFilters(userId, MuscleGroups.CHEST, CardRarity.LEGENDARY, pageable))
                .thenReturn(new PageImpl<>(List.of(card), pageable, 1));
        when(workoutMuscleGroupMapper.toApi(MuscleGroups.CHEST)).thenReturn("peito");

        Page<InventoryCardResponseDTO> result = inventoryService.getUserCards(userId, filters, pageable);

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(inventoryRepository).findCardsWithFilters(
                userIdCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(MuscleGroups.CHEST),
                org.mockito.ArgumentMatchers.eq(CardRarity.LEGENDARY),
                org.mockito.ArgumentMatchers.eq(pageable));

        InventoryCardResponseDTO response = result.getContent().getFirst();
        assertEquals(userId, userIdCaptor.getValue());
        assertEquals(cardId, response.id());
        assertEquals(workoutSessionId, response.workoutSessionId());
        assertEquals(120.0, response.attackPower());
        assertEquals("LEGENDARY", response.rarity());
        assertEquals("Lendário", response.rarityLabel());
        assertEquals("peito", response.muscleGroup());
        assertEquals(Map.of("chest", 4, "deltoids", 2, "triceps", 2), response.muscleIntensities());
        assertEquals(Boolean.TRUE, response.isPersonalRecord());
        assertEquals(80.0, response.bodyWeightAtGeneration());
        assertEquals(LocalDateTime.of(2026, 6, 18, 10, 30), response.generatedAt());
    }
}
