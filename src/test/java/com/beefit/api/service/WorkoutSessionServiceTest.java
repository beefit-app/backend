package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.beefit.api.domain.TemplateExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.StartWorkoutExerciseItemDTO;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.exception.WorkoutAlreadyInProgressException;
import com.beefit.api.exception.WorkoutTemplateNotFoundException;
import com.beefit.api.mapper.WorkoutSessionMapper;
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

    @InjectMocks
    private WorkoutSessionService workoutSessionService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
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
        CatalogExercise secondCatalogExercise = new CatalogExercise();
        secondCatalogExercise.setId(UUID.randomUUID());

        TemplateExercise firstTemplateExercise = new TemplateExercise();
        firstTemplateExercise.setCatalogExercise(firstCatalogExercise);
        firstTemplateExercise.setOrder(1);

        TemplateExercise secondTemplateExercise = new TemplateExercise();
        secondTemplateExercise.setCatalogExercise(secondCatalogExercise);
        secondTemplateExercise.setOrder(2);

        WorkoutTemplate template = new WorkoutTemplate();
        template.setId(templateId);
        template.setUser(user);
        template.setExercises(List.of(firstTemplateExercise, secondTemplateExercise));

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
}
