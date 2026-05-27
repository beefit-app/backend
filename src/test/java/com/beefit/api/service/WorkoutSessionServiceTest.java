package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.Set;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.FinishWorkoutExerciseDTO;
import com.beefit.api.dto.FinishWorkoutSetDTO;
import com.beefit.api.dto.FinishWorkoutSnapshotRequestDTO;
import com.beefit.api.dto.StartWorkoutExerciseItemDTO;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.dto.SocialEventDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.exception.InvalidMuscleGroupException;
import com.beefit.api.exception.InvalidWorkoutSessionStateException;
import com.beefit.api.exception.InvalidWorkoutSnapshotException;
import com.beefit.api.exception.WorkoutAlreadyInProgressException;
import com.beefit.api.exception.WorkoutSessionNotFoundException;
import com.beefit.api.exception.WorkoutTemplateNotFoundException;
import com.beefit.api.mapper.WorkoutSessionMapper;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
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

    @InjectMocks
    private WorkoutSessionService workoutSessionService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setUsername("theo");
        user.setFullName("Theo");
    }

    @Test
    void startWorkoutWithoutTemplateCreatesEmptySessionEvenWhenExercisesAreProvided() {
        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of(MuscleGroups.CHEST),
                Boolean.FALSE,
                null,
                List.of(new StartWorkoutExerciseItemDTO(UUID.randomUUID(), 1)));

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto());

        WorkoutSessionResponseDTO response = assertDoesNotThrow(() -> workoutSessionService.startWorkout(userId, request));

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository).save(sessionCaptor.capture());
        verify(workoutTemplateRepository, never()).findById(any(UUID.class));
        verify(catalogExerciseRepository, never()).findById(any(UUID.class));

        WorkoutSession savedSession = sessionCaptor.getValue();
        assertNotNull(savedSession.getStartedAt());
        assertEquals(WorkoutStatus.IN_PROGRESS, savedSession.getStatus());
        assertEquals(List.of(MuscleGroups.CHEST), savedSession.getMuscleGroupsList());
        assertEquals(Boolean.FALSE, savedSession.getIsPrivate());
        assertEquals(0, savedSession.getExercises().size());
        assertEquals(0, response.exercises().size());
    }

    @Test
    void startWorkoutWithTemplateCopiesTemplateExercisesInOrder() {
        UUID templateId = UUID.randomUUID();
        CatalogExercise firstCatalogExercise = new CatalogExercise();
        firstCatalogExercise.setId(UUID.randomUUID());
        firstCatalogExercise.setOrder(1);
        CatalogExercise secondCatalogExercise = new CatalogExercise();
        secondCatalogExercise.setId(UUID.randomUUID());
        secondCatalogExercise.setOrder(2);

        WorkoutTemplate template = new WorkoutTemplate();
        template.setId(templateId);
        template.setUser(user);
        template.setExerciseList(List.of(firstCatalogExercise, secondCatalogExercise));

        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of(MuscleGroups.BACK),
                Boolean.TRUE,
                templateId,
                null);

        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto());

        workoutSessionService.startWorkout(userId, request);

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository).save(sessionCaptor.capture());
        verify(catalogExerciseRepository, never()).findById(any(UUID.class));

        WorkoutSession savedSession = sessionCaptor.getValue();
        assertEquals(2, savedSession.getExercises().size());
        assertEquals(firstCatalogExercise, savedSession.getExercises().get(0).getExercise());
        assertEquals(1, savedSession.getExercises().get(0).getOrder());
        assertEquals(savedSession, savedSession.getExercises().get(0).getWorkout());
        assertEquals(secondCatalogExercise, savedSession.getExercises().get(1).getExercise());
        assertEquals(2, savedSession.getExercises().get(1).getOrder());
        verify(sseService, never()).broadcast(any(SocialEventDTO.class));
    }

    @Test
    void startWorkoutWithForeignTemplateThrowsNotFound() {
        UUID templateId = UUID.randomUUID();

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        WorkoutTemplate template = new WorkoutTemplate();
        template.setId(templateId);
        template.setUser(otherUser);

        StartWorkoutRequestDTO request = new StartWorkoutRequestDTO(
                List.of(MuscleGroups.LEGS),
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
                List.of(MuscleGroups.CORE),
                Boolean.FALSE,
                null,
                null);

        WorkoutSession inProgress = new WorkoutSession();
        when(workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId))
                .thenReturn(List.of(inProgress));

        assertThrows(WorkoutAlreadyInProgressException.class,
                () -> workoutSessionService.startWorkout(userId, request));
        verify(userRepository, never()).findById(userId);
    }

    @Test
    void finishWorkoutReconcilesExercisesAndSetsFromSnapshot() {
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

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.of(2026, 5, 26, 10, 45),
                List.of(MuscleGroups.CHEST, MuscleGroups.LEGS),
                List.of(
                        new FinishWorkoutExerciseDTO(
                                null,
                                newCatalogExercise.getId(),
                                1,
                                List.of(new FinishWorkoutSetDTO(null, 1, 80.0, 5))),
                        new FinishWorkoutExerciseDTO(
                                existingExercise.getId(),
                                existingCatalogExercise.getId(),
                                2,
                                List.of(
                                        new FinishWorkoutSetDTO(existingSetId, 1, 45.0, 8),
                                        new FinishWorkoutSetDTO(null, 2, 42.5, 10)))));

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(catalogExerciseRepository.findById(newCatalogExercise.getId())).thenReturn(Optional.of(newCatalogExercise));
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(finishedResponseDto(workoutId));

        WorkoutSessionResponseDTO response = workoutSessionService.finishWorkout(userId, workoutId, request);

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository).save(sessionCaptor.capture());

        WorkoutSession savedSession = sessionCaptor.getValue();
        assertEquals(WorkoutStatus.FINISHED, savedSession.getStatus());
        assertEquals(request.finishedAt(), savedSession.getFinishedAt());
        assertEquals(2700, savedSession.getDurationSeconds());
        assertEquals(List.of(MuscleGroups.CHEST, MuscleGroups.LEGS), savedSession.getMuscleGroupsList());
        assertEquals(2, savedSession.getExercises().size());
        assertEquals(newCatalogExercise, savedSession.getExercises().get(0).getExercise());
        assertEquals(existingCatalogExercise, savedSession.getExercises().get(1).getExercise());
        assertNull(removableExercise.getWorkout());

        SessionExercise updatedExercise = savedSession.getExercises().get(1);
        assertEquals(2, updatedExercise.getSets().size());
        assertEquals(45.0, updatedExercise.getSets().get(0).getWeightKg());
        assertEquals(8, updatedExercise.getSets().get(0).getReps());
        assertEquals(42.5, updatedExercise.getSets().get(1).getWeightKg());
        assertEquals(80.0 * 5 + 45.0 * 8 + 42.5 * 10, savedSession.getTotalVolume());
        assertEquals(WorkoutStatus.FINISHED.getStatus(), response.status());
    }

    @Test
    void finishWorkoutDoesNotBroadcastPrivateSession() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setIsPrivate(Boolean.TRUE);

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.of(2026, 5, 26, 10, 30),
                List.of(MuscleGroups.CHEST),
                List.of());

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(finishedResponseDto(workoutId));

        workoutSessionService.finishWorkout(userId, workoutId, request);

        verify(sseService, never()).broadcast(any(SocialEventDTO.class));
    }

    @Test
    void addMuscleGroupUpdatesPublicSessionAndBroadcastsOnce() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutMuscleGroupMapper.toDomain("pernas")).thenReturn(MuscleGroups.LEGS);
        when(workoutMuscleGroupMapper.toApi(List.of(MuscleGroups.CHEST, MuscleGroups.LEGS)))
                .thenReturn(List.of("peito", "pernas"));
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto());

        workoutSessionService.addMuscleGroup(userId, workoutId, "pernas");

        assertEquals(List.of(MuscleGroups.CHEST, MuscleGroups.LEGS), session.getMuscleGroupsList());
        verify(workoutSessionRepository).save(session);

        ArgumentCaptor<SocialEventDTO> eventCaptor = ArgumentCaptor.forClass(SocialEventDTO.class);
        verify(sseService).broadcast(eventCaptor.capture());
        SocialEventDTO event = eventCaptor.getValue();
        assertEquals(SocialEvent.WORKOUT_MUSCLE_GROUP_ADDED, event.type());
        assertEquals(workoutId, event.workoutPresenceEvent().workoutId());
        assertEquals(List.of("peito", "pernas"), event.workoutPresenceEvent().muscleGroups());
    }

    @Test
    void addMuscleGroupIsIdempotentWhenGroupAlreadyExists() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutMuscleGroupMapper.toDomain("peito")).thenReturn(MuscleGroups.CHEST);
        when(workoutSessionMapper.toDto(session)).thenReturn(responseDto());

        workoutSessionService.addMuscleGroup(userId, workoutId, "peito");

        verify(workoutSessionRepository, never()).save(any(WorkoutSession.class));
        verify(sseService, never()).broadcast(any(SocialEventDTO.class));
    }

    @Test
    void addMuscleGroupDoesNotBroadcastPrivateSession() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setIsPrivate(Boolean.TRUE);

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutMuscleGroupMapper.toDomain("pernas")).thenReturn(MuscleGroups.LEGS);
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(responseDto());

        workoutSessionService.addMuscleGroup(userId, workoutId, "pernas");

        assertEquals(List.of(MuscleGroups.CHEST, MuscleGroups.LEGS), session.getMuscleGroupsList());
        verify(sseService, never()).broadcast(any(SocialEventDTO.class));
    }

    @Test
    void addMuscleGroupRejectsInvalidGroup() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));
        when(workoutMuscleGroupMapper.toDomain("invalido")).thenThrow(new IllegalArgumentException());

        assertThrows(InvalidMuscleGroupException.class,
                () -> workoutSessionService.addMuscleGroup(userId, workoutId, "invalido"));

        verify(workoutSessionRepository, never()).save(any(WorkoutSession.class));
    }

    @Test
    void addMuscleGroupRejectsFinishedSession() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        session.setStatus(WorkoutStatus.FINISHED);

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(InvalidWorkoutSessionStateException.class,
                () -> workoutSessionService.addMuscleGroup(userId, workoutId, "pernas"));

        verify(workoutMuscleGroupMapper, never()).toDomain(any(String.class));
    }

    @Test
    void addMuscleGroupRejectsSessionFromAnotherUser() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        session.setUser(otherUser);

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(WorkoutSessionNotFoundException.class,
                () -> workoutSessionService.addMuscleGroup(userId, workoutId, "pernas"));

        verify(workoutMuscleGroupMapper, never()).toDomain(any(String.class));
    }

    @Test
    void finishWorkoutRejectsDuplicateExerciseOrders() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.of(2026, 5, 26, 10, 30),
                List.of(MuscleGroups.CHEST),
                List.of(
                        new FinishWorkoutExerciseDTO(null, UUID.randomUUID(), 1, List.of()),
                        new FinishWorkoutExerciseDTO(null, UUID.randomUUID(), 1, List.of())));

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(InvalidWorkoutSnapshotException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId, request));
    }

    @Test
    void finishWorkoutRejectsExistingExerciseCatalogChange() {
        UUID workoutId = UUID.randomUUID();
        CatalogExercise existingCatalogExercise = catalogExercise(UUID.randomUUID(), "Supino");
        SessionExercise existingExercise = sessionExercise(UUID.randomUUID(), existingCatalogExercise, 1);
        WorkoutSession session = inProgressSession(workoutId);
        session.addExercise(existingExercise);

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.of(2026, 5, 26, 10, 30),
                List.of(MuscleGroups.CHEST),
                List.of(new FinishWorkoutExerciseDTO(
                        existingExercise.getId(),
                        UUID.randomUUID(),
                        1,
                        List.of())));

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(InvalidWorkoutSnapshotException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId, request));
    }

    @Test
    void finishWorkoutRejectsSessionFromAnotherUser() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        session.setUser(otherUser);

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.of(2026, 5, 26, 10, 30),
                List.of(MuscleGroups.CHEST),
                List.of());

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(WorkoutSessionNotFoundException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId, request));
    }

    @Test
    void finishWorkoutRejectsFinishedAtBeforeWorkoutStart() {
        UUID workoutId = UUID.randomUUID();
        WorkoutSession session = inProgressSession(workoutId);

        FinishWorkoutSnapshotRequestDTO request = new FinishWorkoutSnapshotRequestDTO(
                LocalDateTime.of(2026, 5, 26, 9, 59),
                List.of(MuscleGroups.CHEST),
                List.of());

        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertThrows(InvalidWorkoutSnapshotException.class,
                () -> workoutSessionService.finishWorkout(userId, workoutId, request));
    }

    private WorkoutSessionResponseDTO responseDto() {
        return new WorkoutSessionResponseDTO(
                UUID.randomUUID(),
                WorkoutStatus.IN_PROGRESS.getStatus(),
                LocalDateTime.now(),
                null,
                null,
                List.of(MuscleGroups.CHEST.getMuscle()),
                Boolean.FALSE,
                List.of());
    }

    private WorkoutSessionResponseDTO finishedResponseDto(UUID workoutId) {
        return new WorkoutSessionResponseDTO(
                workoutId,
                WorkoutStatus.FINISHED.getStatus(),
                LocalDateTime.of(2026, 5, 26, 10, 0),
                LocalDateTime.of(2026, 5, 26, 10, 45),
                2700,
                List.of(MuscleGroups.CHEST.getMuscle(), MuscleGroups.LEGS.getMuscle()),
                Boolean.FALSE,
                List.of());
    }

    private WorkoutSession inProgressSession(UUID workoutId) {
        WorkoutSession session = new WorkoutSession();
        session.setId(workoutId);
        session.setUser(user);
        session.setStartedAt(LocalDateTime.of(2026, 5, 26, 10, 0));
        session.setStatus(WorkoutStatus.IN_PROGRESS);
        session.setMuscleGroupsList(new ArrayList<>(List.of(MuscleGroups.CHEST)));
        session.setIsPrivate(Boolean.FALSE);
        return session;
    }

    private CatalogExercise catalogExercise(UUID id, String name) {
        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(id);
        catalogExercise.setName(name);
        catalogExercise.setWgerId(1L);
        return catalogExercise;
    }

    private SessionExercise sessionExercise(UUID id, CatalogExercise catalogExercise, Integer order) {
        SessionExercise sessionExercise = new SessionExercise();
        sessionExercise.setId(id);
        sessionExercise.setExercise(catalogExercise);
        sessionExercise.setOrder(order);
        return sessionExercise;
    }

    private Set set(UUID id, Integer order, Double weightKg, Integer reps) {
        Set set = new Set();
        set.setId(id);
        set.setOrder(order);
        set.setWeightKg(weightKg);
        set.setReps(reps);
        set.setIsPersonalRecord(Boolean.FALSE);
        return set;
    }
}
