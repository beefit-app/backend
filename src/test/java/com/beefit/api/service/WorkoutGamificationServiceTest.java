package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.Card;
import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.workout.CardGeneratedEventDTO;
import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutGamificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private CardService cardService;

    @Mock
    private SseService sseService;

    @Mock
    private WorkoutMuscleGroupMapper workoutMuscleGroupMapper;

    @InjectMocks
    private WorkoutGamificationService workoutGamificationService;

    private User user;
    private WorkoutSession session;
    private UUID userId;
    private UUID workoutId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workoutId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setWorkoutsCount(0);
        user.setOffensiveHoneys(0);
        user.setWeeklyWorkoutsCount(0);
        user.setBodyWeight(80.0F);

        session = new WorkoutSession();
        session.setId(workoutId);
        session.setUser(user);
        session.setStatus(WorkoutStatus.FINISHED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
    }

    @Test
    void workoutBelowThirtyMinutesDoesNotIncrementHoneys() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.of(2026, 5, 26, 10, 28),
                1700, true, "Treino Rápido", false, false, null);

        workoutGamificationService.processWorkoutFinished(event);

        assertEquals(0, user.getOffensiveHoneys());
        assertEquals(0, user.getWeeklyWorkoutsCount());
        assertEquals(1, user.getWorkoutsCount());
        verify(workoutSessionRepository, never()).existsEligibleFinishedWorkoutOnDay(
                any(), any(), any(), any(Integer.class), any(), any());
        verify(userRepository).save(user);
    }

    @Test
    void workoutAtThirtyMinutesWithCompletedSetIncrementsHoney() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.of(2026, 5, 26, 10, 30),
                1800, true, "Treino Normal", false, false, null);

        when(workoutSessionRepository.existsEligibleFinishedWorkoutOnDay(
                eq(userId), eq(workoutId), eq(WorkoutStatus.FINISHED), eq(1740),
                eq(LocalDateTime.of(2026, 5, 26, 0, 0)), eq(LocalDateTime.of(2026, 5, 27, 0, 0))))
                .thenReturn(false);

        workoutGamificationService.processWorkoutFinished(event);

        assertEquals(1, user.getOffensiveHoneys());
        assertEquals(1, user.getWeeklyWorkoutsCount());
        assertEquals(1, user.getWorkoutsCount());
        verify(userRepository).save(user);
    }

    @Test
    void secondEligibleWorkoutOnSameDayDoesNotIncrementHoneysAgain() {
        user.setOffensiveHoneys(5);
        user.setWeeklyWorkoutsCount(2);

        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.of(2026, 5, 26, 18, 0),
                1800, true, "Segundo Treino", false, false, null);

        when(workoutSessionRepository.existsEligibleFinishedWorkoutOnDay(
                eq(userId), eq(workoutId), eq(WorkoutStatus.FINISHED), eq(1740),
                eq(LocalDateTime.of(2026, 5, 26, 0, 0)), eq(LocalDateTime.of(2026, 5, 27, 0, 0))))
                .thenReturn(true);

        workoutGamificationService.processWorkoutFinished(event);

        assertEquals(5, user.getOffensiveHoneys());
        assertEquals(2, user.getWeeklyWorkoutsCount());
        assertEquals(1, user.getWorkoutsCount());
        verify(userRepository).save(user);
    }

    @Test
    void workoutWithoutCompletedSetsDoesNotIncrementHoneys() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.of(2026, 5, 26, 10, 30),
                1800, false, "Treino Sem Séries", false, false, null);

        workoutGamificationService.processWorkoutFinished(event);

        assertEquals(0, user.getOffensiveHoneys());
        assertEquals(0, user.getWeeklyWorkoutsCount());
        assertEquals(1, user.getWorkoutsCount());
        verify(workoutSessionRepository, never()).existsEligibleFinishedWorkoutOnDay(
                any(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void generatesCardAndBroadcastsSseWhenExtraordinaryPerformanceFound() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.of(2026, 5, 26, 10, 45),
                2700, true, "Treino com Carta", false, false, null);

        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(UUID.randomUUID());
        catalogExercise.setName("Supino");

        Card generatedCard = new Card();
        generatedCard.setId(UUID.randomUUID());
        generatedCard.setExercise(catalogExercise);
        generatedCard.setBestSetWeightKg(80.0);
        generatedCard.setBestSetReps(5);
        generatedCard.setEstimatedOneRepMax(93.33);
        generatedCard.setImprovementPercentage(9.0);
        generatedCard.setCardRarity(CardRarity.RARE);
        generatedCard.setMuscleGroup(MuscleGroups.CHEST);
        generatedCard.setMuscleIntensities(Map.of("chest", 4, "deltoids", 2, "triceps", 2));

        when(cardService.generateCard(user, session)).thenReturn(generatedCard);
        when(workoutMuscleGroupMapper.toApi(MuscleGroups.CHEST)).thenReturn("peito");

        workoutGamificationService.processWorkoutFinished(event);

        verify(workoutSessionRepository).save(session);
        assertEquals(generatedCard, session.getCard());

        ArgumentCaptor<SocialEventDTO> sseCaptor = ArgumentCaptor.forClass(SocialEventDTO.class);
        verify(sseService).broadcastToFriend(sseCaptor.capture(), eq(userId));
        assertEquals(SocialEvent.CARD_GENERATED, sseCaptor.getValue().type());

        CardGeneratedEventDTO sseData = (CardGeneratedEventDTO) sseCaptor.getValue().dataSocialEvent();
        assertEquals(workoutId, sseData.workoutId());
        assertEquals(generatedCard.getId(), sseData.cardId());
        assertEquals(93.33, sseData.attackPower());
        assertEquals("RARE", sseData.rarity());
        assertEquals("peito", sseData.muscleGroup());
        assertTrue(sseData.isPersonalRecord());
    }

    @Test
    void doesNotBroadcastWhenCardNotGenerated() {
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.of(2026, 5, 26, 10, 45),
                1800, true, "Treino Comum", false, false, null);

        when(cardService.generateCard(user, session)).thenReturn(null);

        workoutGamificationService.processWorkoutFinished(event);

        verify(sseService, never()).broadcastToFriend(any(), any());
    }
}
