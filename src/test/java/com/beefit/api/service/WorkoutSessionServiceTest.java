package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.context.ApplicationEventPublisher;

import com.beefit.api.event.workout.WorkoutFinishedEvent;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.Card;
import com.beefit.api.domain.Inventory;
import com.beefit.api.domain.Post;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.CardRarity;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.workout.CardGeneratedEventDTO;
import com.beefit.api.dto.workout.ExerciseHistoryResponseDTO;
import com.beefit.api.dto.workout.ExerciseHistorySummaryProjection;
import com.beefit.api.dto.workout.FinishWorkoutExerciseDTO;
import com.beefit.api.dto.workout.FinishWorkoutSetDTO;
import com.beefit.api.dto.workout.FinishWorkoutSnapshotRequestDTO;
import com.beefit.api.dto.workout.StartWorkoutExerciseItemDTO;
import com.beefit.api.dto.workout.StartWorkoutRequestDTO;
import com.beefit.api.exception.workout.InvalidWorkoutSnapshotException;
import com.beefit.api.dto.workout.WorkoutSessionResponseDTO;
import com.beefit.api.exception.workout.InvalidMuscleGroupException;
import com.beefit.api.exception.workout.InvalidWorkoutSessionStateException;
import com.beefit.api.exception.workout.WorkoutAlreadyInProgressException;
import com.beefit.api.exception.workout.WorkoutSessionNotFoundException;
import com.beefit.api.exception.workout.WorkoutTemplateNotFoundException;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.mapper.WorkoutSessionMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.ReportRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;

@ExtendWith(MockitoExtension.class)
class WorkoutSessionServiceTest {

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private WorkoutTemplateRepository workoutTemplateRepository;

    @Mock
    private CatalogExerciseRepository catalogExerciseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkoutSessionMapper workoutSessionMapper;

    @Mock
    private WorkoutMuscleGroupMapper workoutMuscleGroupMapper;

    @Mock
    private SseService sseService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PostRepository postRepository;

    @Mock
    private FriendshipService friendshipService;

    @Mock
    private WorkoutMediaService workoutMediaService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private Clock businessClock;

    @InjectMocks
    private WorkoutSessionService workoutSessionService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = user(userId);
        lenient().when(businessClock.instant()).thenReturn(Instant.parse("2026-05-26T15:00:00Z"));
        lenient().when(businessClock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void startWorkoutWithoutTemplateCreatesEmptySession() {
        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of("peito"),
                Boolean.TRUE,
                null,
                List.of());

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutMuscleGroupMapper.toDomain(List.of("peito"))).thenReturn(List.of(MuscleGroups.CHEST));
        when(workoutSessionRepository.saveAndFlush(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto(WorkoutStatus.IN_PROGRESS));

        WorkoutSessionResponseDTO response = assertDoesNotThrow(() -> workoutSessionService.startWorkout(userId, request));

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository).saveAndFlush(sessionCaptor.capture());
        verify(workoutTemplateRepository, never()).findById(any(UUID.class));

        WorkoutSession savedSession = sessionCaptor.getValue();
        assertNotNull(savedSession.getStartedAt());
        assertEquals(WorkoutStatus.IN_PROGRESS, savedSession.getStatus());
        assertEquals(List.of(MuscleGroups.CHEST), savedSession.getMuscleGroupsList());
        assertEquals(Boolean.TRUE, savedSession.getIsPrivate());
        assertEquals(0, savedSession.getExercises().size());
        assertEquals("in_progress", response.status());
    }

    @Test
    void startWorkoutWithoutTemplatePersistsRequestedExercisesInOrder() {
        UUID firstCatalogId = UUID.randomUUID();
        UUID secondCatalogId = UUID.randomUUID();
        CatalogExercise firstCatalogExercise = catalogExercise(firstCatalogId, "Supino");
        CatalogExercise secondCatalogExercise = catalogExercise(secondCatalogId, "Puxada");
        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of("peito"),
                Boolean.TRUE,
                null,
                List.of(
                        new StartWorkoutExerciseItemDTO(secondCatalogId, 2),
                        new StartWorkoutExerciseItemDTO(firstCatalogId, 1)));

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutMuscleGroupMapper.toDomain(List.of("peito"))).thenReturn(List.of(MuscleGroups.CHEST));
        when(catalogExerciseRepository.findById(firstCatalogId)).thenReturn(Optional.of(firstCatalogExercise));
        when(catalogExerciseRepository.findById(secondCatalogId)).thenReturn(Optional.of(secondCatalogExercise));
        when(workoutSessionRepository.saveAndFlush(any(WorkoutSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto(WorkoutStatus.IN_PROGRESS));

        workoutSessionService.startWorkout(userId, request);

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository).saveAndFlush(sessionCaptor.capture());
        WorkoutSession savedSession = sessionCaptor.getValue();
        assertEquals(2, savedSession.getExercises().size());
        assertEquals(firstCatalogExercise, savedSession.getExercises().get(0).getExercise());
        assertEquals(1, savedSession.getExercises().get(0).getOrder());
        assertEquals(savedSession, savedSession.getExercises().get(0).getWorkoutSession());
        assertEquals(secondCatalogExercise, savedSession.getExercises().get(1).getExercise());
        assertEquals(2, savedSession.getExercises().get(1).getOrder());
    }

    @Test
    void startWorkoutRejectsDuplicateRequestedExerciseOrders() {
        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of("peito"),
                Boolean.TRUE,
                null,
                List.of(
                        new StartWorkoutExerciseItemDTO(UUID.randomUUID(), 1),
                        new StartWorkoutExerciseItemDTO(UUID.randomUUID(), 1)));

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutMuscleGroupMapper.toDomain(List.of("peito"))).thenReturn(List.of(MuscleGroups.CHEST));

        assertThrows(InvalidWorkoutSnapshotException.class,
                () -> workoutSessionService.startWorkout(userId, request));
        verify(workoutSessionRepository, never()).saveAndFlush(any(WorkoutSession.class));
    }

    @Test
    void startWorkoutWithTemplateCopiesTemplateExercisesInOrder() {
        UUID templateId = UUID.randomUUID();
        CatalogExercise firstCatalogExercise = catalogExercise(UUID.randomUUID(), "Supino");
        CatalogExercise secondCatalogExercise = catalogExercise(UUID.randomUUID(), "Puxada");

        WorkoutTemplate template = new WorkoutTemplate();
        template.setId(templateId);
        template.setUser(user);
        template.setExerciseList(List.of(
                templateExercise(firstCatalogExercise, 1),
                templateExercise(secondCatalogExercise, 2)));

        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of("costas"),
                Boolean.TRUE,
                templateId,
                null);

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(workoutMuscleGroupMapper.toDomain(List.of("costas"))).thenReturn(List.of(MuscleGroups.BACK));
        when(workoutSessionRepository.saveAndFlush(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto(WorkoutStatus.IN_PROGRESS));

        workoutSessionService.startWorkout(userId, request);

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository).saveAndFlush(sessionCaptor.capture());

        WorkoutSession savedSession = sessionCaptor.getValue();
        assertEquals(2, savedSession.getExercises().size());
        assertEquals(firstCatalogExercise, savedSession.getExercises().get(0).getExercise());
        assertEquals(1, savedSession.getExercises().get(0).getOrder());
        assertEquals(savedSession, savedSession.getExercises().get(0).getWorkoutSession());
        assertEquals(secondCatalogExercise, savedSession.getExercises().get(1).getExercise());
        assertEquals(2, savedSession.getExercises().get(1).getOrder());
    }

    @Test
    void startWorkoutWithForeignTemplateThrowsNotFound() {
        UUID templateId = UUID.randomUUID();
        WorkoutTemplate template = new WorkoutTemplate();
        template.setUser(user(UUID.randomUUID()));

        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of("pernas"),
                Boolean.TRUE,
                templateId,
                null);

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        assertThrows(WorkoutTemplateNotFoundException.class,
                () -> workoutSessionService.startWorkout(userId, request));
    }

    @Test
    void startWorkoutWhenWorkoutAlreadyInProgressThrows() {
        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of("core"),
                Boolean.FALSE,
                null,
                null);

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId))
                .thenReturn(List.of(new WorkoutSession()));

        assertThrows(WorkoutAlreadyInProgressException.class,
                () -> workoutSessionService.startWorkout(userId, request));
        verify(userRepository, never()).findById(userId);
    }

    @Test
    void getWorkoutReturnsOwnedSessionForRecovery() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        WorkoutSessionResponseDTO expected = responseDto(WorkoutStatus.IN_PROGRESS);
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutSessionMapper.toDto(session)).thenReturn(expected);

        assertEquals(expected, workoutSessionService.getWorkout(userId, workoutId));
    }

    @Test
    void getWorkoutHidesAnotherUsersSession() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setUser(user(UUID.randomUUID()));
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(WorkoutSessionNotFoundException.class,
                () -> workoutSessionService.getWorkout(userId, workoutId));
    }

    @Test
    void getExerciseHistoryReturnsLatestAndBestCompletedPerformance() {
        UUID catalogExerciseId = UUID.randomUUID();

        ExerciseHistorySummaryProjection latestProj = historyProjection(
                LocalDateTime.of(2026, 5, 25, 10, 0), 90.0, 8, 1L, 2L);
        ExerciseHistorySummaryProjection bestProj = historyProjection(
                LocalDateTime.of(2026, 5, 20, 10, 0), 100.0, 5, 2L, 1L);

        when(workoutSessionRepository.findExerciseHistorySummary(userId, catalogExerciseId))
                .thenReturn(List.of(latestProj, bestProj));

        ExerciseHistoryResponseDTO history = workoutSessionService.getExerciseHistory(userId, catalogExerciseId);

        assertEquals(LocalDateTime.of(2026, 5, 25, 10, 0), history.lastPerformedAt());
        assertEquals(100.0, history.bestWeightKg());
        assertEquals(5, history.bestReps());
        assertEquals(90.0, history.lastWeightKg());
        assertEquals(8, history.lastReps());
    }

    @Test
    void getExerciseHistoryReturnsEmptyWhenNoHistoryFound() {
        UUID catalogExerciseId = UUID.randomUUID();
        when(workoutSessionRepository.findExerciseHistorySummary(userId, catalogExerciseId))
                .thenReturn(List.of());

        ExerciseHistoryResponseDTO history = workoutSessionService.getExerciseHistory(userId, catalogExerciseId);

        assertNotNull(history);
        assertNull(history.lastPerformedAt());
        assertNull(history.bestWeightKg());
        assertNull(history.bestReps());
        assertNull(history.lastWeightKg());
        assertNull(history.lastReps());
    }

    @Test
    void finishWorkoutReconcilesExercisesAndUpdatesMetrics() {
        UUID workoutId = UUID.randomUUID();
        UUID existingSetId = UUID.randomUUID();

        CatalogExercise existingCatalogExercise = catalogExercise(UUID.randomUUID(), "Supino");
        CatalogExercise removableCatalogExercise = catalogExercise(UUID.randomUUID(), "Puxada");
        CatalogExercise newCatalogExercise = catalogExercise(UUID.randomUUID(), "Agachamento");

        SessionExercise existingExercise = sessionExercise(UUID.randomUUID(), existingCatalogExercise, 1);
        existingExercise.addSet(set(existingSetId, 1, 40.0, 10));

        SessionExercise removableExercise = sessionExercise(UUID.randomUUID(), removableCatalogExercise, 2);
        removableExercise.addSet(set(UUID.randomUUID(), 1, 20.0, 12));

        WorkoutSession session = inProgressSession(workoutId);
        session.addExercise(existingExercise);
        session.addExercise(removableExercise);

        String finishedAt = "2026-05-26T10:45:00-03:00";
        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                finishedAt,
                List.of("peito", "pernas"),
                List.of(
                        new FinishWorkoutExerciseDTO(
                                null,
                                newCatalogExercise.getId().toString(),
                                newCatalogExercise.getName(),
                                1,
                                List.of(new FinishWorkoutSetDTO(null, 1, 80.0, 5))),
                        new FinishWorkoutExerciseDTO(
                                existingExercise.getId(),
                                existingCatalogExercise.getId().toString(),
                                existingCatalogExercise.getName(),
                                2,
                                List.of(
                                        new FinishWorkoutSetDTO(existingSetId, 1, 45.0, 8),
                                        new FinishWorkoutSetDTO(null, 2, 42.5, 10)))),
                null,
                false,
                null);

        when(workoutSessionRepository.findByIdForFinish(workoutId)).thenReturn(Optional.of(session));
        when(catalogExerciseRepository.findById(newCatalogExercise.getId())).thenReturn(Optional.of(newCatalogExercise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutMuscleGroupMapper.toDomain(List.of("peito", "pernas"))).thenReturn(List.of(MuscleGroups.CHEST, MuscleGroups.LEGS));
        when(workoutSessionRepository.saveAndFlush(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto(WorkoutStatus.FINISHED));

        WorkoutSessionResponseDTO response = workoutSessionService.finishWorkout(userId, workoutId, "finish-1", request);

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository).saveAndFlush(sessionCaptor.capture());

        WorkoutSession savedSession = sessionCaptor.getValue();
        assertEquals(WorkoutStatus.FINISHED, savedSession.getStatus());
        assertEquals(LocalDateTime.of(2026, 5, 26, 10, 45), savedSession.getFinishedAt());
        assertEquals(2700, savedSession.getDurationSeconds());
        assertEquals(List.of(MuscleGroups.CHEST, MuscleGroups.LEGS), savedSession.getMuscleGroupsList());
        assertEquals(2, savedSession.getExercises().size());
        assertEquals(newCatalogExercise, savedSession.getExercises().get(0).getExercise());
        assertEquals(existingCatalogExercise, savedSession.getExercises().get(1).getExercise());
        assertEquals(80.0 * 5 + 45.0 * 8 + 42.5 * 10, savedSession.getTotalVolume());
        assertEquals("finished", response.status());

        ArgumentCaptor<WorkoutFinishedEvent> eventCaptor = ArgumentCaptor.forClass(WorkoutFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        WorkoutFinishedEvent event = eventCaptor.getValue();
        assertEquals(workoutId, event.workoutSessionId());
        assertEquals(userId, event.userId());
        assertEquals(LocalDateTime.of(2026, 5, 26, 10, 45), event.finishedAt());
        assertEquals(2700, event.durationSeconds());
        assertTrue(event.hasCompletedSets());
        assertFalse(event.shouldPostWorkout());

        WorkoutSessionResponseDTO replay = workoutSessionService.finishWorkout(userId, workoutId, "finish-1", request);
        assertEquals(response, replay);
        verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(any(WorkoutFinishedEvent.class));
    }

    @Test
    void finishWorkoutBelowThirtyMinutesPublishesEventWithShortDuration() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        FinishWorkoutSnapshotRequestDTO request = minimalFinishRequest("2026-05-26T10:28:59-03:00");

        prepareMinimalFinish(workoutId, session);

        workoutSessionService.finishWorkout(userId, workoutId, "finish-short", request);

        ArgumentCaptor<WorkoutFinishedEvent> eventCaptor = ArgumentCaptor.forClass(WorkoutFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(workoutId, eventCaptor.getValue().workoutSessionId());
        assertTrue(eventCaptor.getValue().durationSeconds() < 1740);
    }

    @Test
    void finishWorkoutAtThirtyMinutesWithCompletedSetPublishesEventWithCompletedSetsTrue() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        FinishWorkoutSnapshotRequestDTO request = finishRequestWithCompletedSet(
                session, "2026-05-26T10:30:00-03:00", 8);

        prepareMinimalFinish(workoutId, session);

        workoutSessionService.finishWorkout(userId, workoutId, "finish-thirty-minutes", request);

        ArgumentCaptor<WorkoutFinishedEvent> eventCaptor = ArgumentCaptor.forClass(WorkoutFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(workoutId, eventCaptor.getValue().workoutSessionId());
        assertEquals(1800, eventCaptor.getValue().durationSeconds());
        assertTrue(eventCaptor.getValue().hasCompletedSets());
    }

    @Test
    void finishThirtyMinuteWorkoutWithoutCompletedSetPublishesEventWithCompletedSetsFalse() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        FinishWorkoutSnapshotRequestDTO request = minimalFinishRequest("2026-05-26T10:30:00-03:00");

        prepareMinimalFinish(workoutId, session);

        workoutSessionService.finishWorkout(userId, workoutId, "finish-empty", request);

        ArgumentCaptor<WorkoutFinishedEvent> eventCaptor = ArgumentCaptor.forClass(WorkoutFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(workoutId, eventCaptor.getValue().workoutSessionId());
        assertFalse(eventCaptor.getValue().hasCompletedSets());
    }

    @Test
    void finishThirtyMinuteWorkoutWithZeroRepetitionsPublishesEventWithCompletedSetsFalse() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        FinishWorkoutSnapshotRequestDTO request = finishRequestWithCompletedSet(
                session, "2026-05-26T10:30:00-03:00", 0);

        prepareMinimalFinish(workoutId, session);

        workoutSessionService.finishWorkout(userId, workoutId, "finish-zero-reps", request);

        ArgumentCaptor<WorkoutFinishedEvent> eventCaptor = ArgumentCaptor.forClass(WorkoutFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(workoutId, eventCaptor.getValue().workoutSessionId());
        assertFalse(eventCaptor.getValue().hasCompletedSets());
    }

    @Test
    void finishWorkoutRejectsFinishedAtBeyondClockTolerance() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        when(workoutSessionRepository.findByIdForFinish(workoutId)).thenReturn(Optional.of(session));

        InvalidWorkoutSnapshotException exception = assertThrows(InvalidWorkoutSnapshotException.class,
                () -> workoutSessionService.finishWorkout(
                        userId,
                        workoutId,
                        "finish-future",
                        minimalFinishRequest("2026-05-26T12:05:01-03:00")));

        assertEquals("A data de finalização não pode estar no futuro.", exception.getMessage());
        verify(userRepository, never()).findById(userId);
    }

    @Test
    void finishWorkoutNormalizesAcceptedClockSkewToServerTime() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        prepareMinimalFinish(workoutId, session);

        workoutSessionService.finishWorkout(
                userId,
                workoutId,
                "finish-clock-skew",
                minimalFinishRequest("2026-05-26T12:04:00-03:00"));

        assertEquals(LocalDateTime.of(2026, 5, 26, 12, 0), session.getFinishedAt());
        assertEquals(7200, session.getDurationSeconds());
    }

    @Test
    void finishWorkoutRejectsDurationLongerThanTwentyFourHours() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setStartedAt(LocalDateTime.of(2026, 5, 25, 11, 59));
        when(workoutSessionRepository.findByIdForFinish(workoutId)).thenReturn(Optional.of(session));

        InvalidWorkoutSnapshotException exception = assertThrows(InvalidWorkoutSnapshotException.class,
                () -> workoutSessionService.finishWorkout(
                        userId,
                        workoutId,
                        "finish-too-long",
                        minimalFinishRequest("2026-05-26T12:00:00-03:00")));

        assertEquals("A duração do treino não pode ultrapassar 24 horas.", exception.getMessage());
        verify(userRepository, never()).findById(userId);
    }

    @Test
    void finishWorkoutWithForeignSessionThrowsNotFound() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setUser(user(UUID.randomUUID()));

        when(workoutSessionRepository.findByIdForFinish(workoutId)).thenReturn(Optional.of(session));

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.now().toString(),
                List.of("peito"),
                List.of(),
                null,
                false,
                null);

        assertThrows(WorkoutSessionNotFoundException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId, "finish-1", request));
    }

    @Test
    void finishWorkoutWhenSessionIsNotInProgressThrowsInvalidState() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setStatus(WorkoutStatus.FINISHED);

        when(workoutSessionRepository.findByIdForFinish(workoutId)).thenReturn(Optional.of(session));

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.now().toString(),
                List.of("peito"),
                List.of(),
                null,
                false,
                null);

        assertThrows(InvalidWorkoutSessionStateException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId, "finish-1", request));
    }

    @Test
    void deleteWorkoutOfAnotherUserDeletesNothing() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setUser(user(UUID.randomUUID()));
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(WorkoutSessionNotFoundException.class,
                () -> workoutSessionService.deleteWorkout(userId, workoutId));

        verify(postRepository, never()).delete(any(Post.class));
        verify(reportRepository, never()).deleteByTargetPostId(any());
        verify(workoutSessionRepository, never()).delete(any(WorkoutSession.class));
        verify(workoutMediaService, never()).delete(any());
    }

    @Test
    void deleteWorkoutRemovesPostReportsCardAndPhoto() {
        UUID workoutId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setPhotoMediaId(photoId);
        Inventory inventory = new Inventory();
        Card card = new Card();
        inventory.addCard(card);
        session.setCard(card);
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(postRepository.findByWorkoutSessionId(workoutId)).thenReturn(Optional.of(post));

        workoutSessionService.deleteWorkout(userId, workoutId);

        verify(reportRepository).deleteByTargetPostId(post.getId());
        verify(postRepository).delete(post);
        assertEquals(null, session.getCard());
        assertTrue(inventory.getCards().isEmpty());
        assertEquals(0, inventory.getTotalCards());
        verify(workoutSessionRepository).delete(session);
        verify(workoutMediaService).delete(photoId);
    }

    @Test
    void deleteWorkoutWithoutPostOrCardOnlyDeletesSession() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(postRepository.findByWorkoutSessionId(workoutId)).thenReturn(Optional.empty());

        workoutSessionService.deleteWorkout(userId, workoutId);

        verify(workoutSessionRepository).delete(session);
        verify(reportRepository, never()).deleteByTargetPostId(any());
        verify(workoutMediaService, never()).delete(any());
    }

    @Test
    void addMuscleGroupPersistsNewGroup() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setMuscleGroupsList(new java.util.ArrayList<>(List.of(MuscleGroups.CHEST)));

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutMuscleGroupMapper.toDomain("costas")).thenReturn(MuscleGroups.BACK);
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto(WorkoutStatus.IN_PROGRESS));

        workoutSessionService.addMuscleGroup(userId, workoutId, "costas");

        assertEquals(List.of(MuscleGroups.CHEST, MuscleGroups.BACK), session.getMuscleGroupsList());
        verify(workoutSessionRepository).save(session);
    }

    @Test
    void addMuscleGroupWithInvalidValueThrowsDomainException() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setMuscleGroupsList(new java.util.ArrayList<>(List.of(MuscleGroups.CHEST)));

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutMuscleGroupMapper.toDomain("foo")).thenThrow(IllegalArgumentException.class);

        assertThrows(InvalidMuscleGroupException.class,
                () -> workoutSessionService.addMuscleGroup(userId, workoutId, "foo"));
    }

    private FinishWorkoutSnapshotRequestDTO minimalFinishRequest(String finishedAt) {
        return new FinishWorkoutSnapshotRequestDTO(
                finishedAt,
                List.of("peito"),
                List.of(),
                "Treino",
                false,
                null);
    }

    private FinishWorkoutSnapshotRequestDTO finishRequestWithCompletedSet(
            WorkoutSession session, String finishedAt, int reps) {
        CatalogExercise catalogExercise = catalogExercise(UUID.randomUUID(), "Supino reto");
        SessionExercise exercise = sessionExercise(UUID.randomUUID(), catalogExercise, 1);
        com.beefit.api.domain.Set completedSet = set(UUID.randomUUID(), 1, 80.0, reps);
        exercise.addSet(completedSet);
        session.addExercise(exercise);

        return new FinishWorkoutSnapshotRequestDTO(
                finishedAt,
                List.of("peito"),
                List.of(new FinishWorkoutExerciseDTO(
                        exercise.getId(),
                        catalogExercise.getId().toString(),
                        catalogExercise.getName(),
                        1,
                        List.of(new FinishWorkoutSetDTO(completedSet.getId(), 1, 80.0, reps)))),
                "Treino",
                false,
                null);
    }

    private void prepareMinimalFinish(UUID workoutId, WorkoutSession session) {
        when(workoutSessionRepository.findByIdForFinish(workoutId)).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutMuscleGroupMapper.toDomain(List.of("peito"))).thenReturn(List.of(MuscleGroups.CHEST));
        when(workoutSessionRepository.saveAndFlush(any(WorkoutSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class)))
                .thenReturn(responseDto(WorkoutStatus.FINISHED));
    }

    private User user(UUID id) {
        User testUser = new User();
        testUser.setId(id);
        testUser.setFullName("Test User");
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setBodyWeight(80.0f);
        testUser.setOffensiveHoneys(0);
        testUser.setWorkoutsCount(0);
        return testUser;
    }

    private WorkoutSession inProgressSession(UUID workoutId) {
        WorkoutSession session = new WorkoutSession();
        session.setId(workoutId);
        session.setUser(user);
        session.setStartedAt(LocalDateTime.of(2026, 5, 26, 10, 0));
        session.setStatus(WorkoutStatus.IN_PROGRESS);
        session.setIsPrivate(Boolean.TRUE);
        session.setMuscleGroupsList(new java.util.ArrayList<>(List.of(MuscleGroups.CHEST)));
        return session;
    }

    private CatalogExercise catalogExercise(UUID id, String name) {
        CatalogExercise exercise = new CatalogExercise();
        exercise.setId(id);
        exercise.setName(name);
        return exercise;
    }

    private TemplateExercise templateExercise(CatalogExercise catalogExercise, int order) {
        TemplateExercise exercise = new TemplateExercise();
        exercise.setCatalogExercise(catalogExercise);
        exercise.setOrder(order);
        return exercise;
    }

    private SessionExercise sessionExercise(UUID id, CatalogExercise catalogExercise, int order) {
        SessionExercise exercise = new SessionExercise();
        exercise.setId(id);
        exercise.setExercise(catalogExercise);
        exercise.setOrder(order);
        return exercise;
    }

    private com.beefit.api.domain.Set set(UUID id, int order, double weightKg, int reps) {
        com.beefit.api.domain.Set set = new com.beefit.api.domain.Set();
        set.setId(id);
        set.setOrder(order);
        set.setWeightKg(weightKg);
        set.setReps(reps);
        set.setIsPersonalRecord(Boolean.FALSE);
        return set;
    }

    private WorkoutSessionResponseDTO responseDto(WorkoutStatus status) {
        return new WorkoutSessionResponseDTO(
                UUID.randomUUID(),
                status.getStatus(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                List.of("peito"),
                Boolean.TRUE,
                List.of(),
                null);
    }

    private ExerciseHistorySummaryProjection historyProjection(
            LocalDateTime finishedAt, Double weightKg, Integer reps, Long rnLatest, Long rnBest) {
        return new ExerciseHistorySummaryProjection() {
            @Override
            public LocalDateTime getFinishedAt() {
                return finishedAt;
            }

            @Override
            public Double getWeightKg() {
                return weightKg;
            }

            @Override
            public Integer getReps() {
                return reps;
            }

            @Override
            public Long getRnLatest() {
                return rnLatest;
            }

            @Override
            public Long getRnBest() {
                return rnBest;
            }
        };
    }
}
