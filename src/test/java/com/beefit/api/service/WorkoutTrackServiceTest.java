package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.Set;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.FinishWorkoutExerciseDTO;
import com.beefit.api.dto.FinishWorkoutSetDTO;
import com.beefit.api.dto.FinishWorkoutSnapshotRequestDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.dto.WorkoutTemplateExerciseRequestDTO;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.mapper.WorkoutSessionMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkoutTrackServiceTest {

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
    private SseService sseService;

    private WorkoutSessionService workoutSessionService;
    private WorkoutTemplateService workoutTemplateService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setBodyWeight(80.0F);
        user.setWorkoutsCount(0);
        user.setWeeklyWorkoutsCount(0);
        user.setOffensiveHoneys(0);

        workoutSessionService = new WorkoutSessionService(
                workoutSessionRepository,
                workoutTemplateRepository,
                catalogExerciseRepository,
                userRepository,
                workoutSessionMapper,
                new WorkoutMuscleGroupMapper(),
                sseService);

        workoutTemplateService = new WorkoutTemplateService(
                workoutTemplateRepository,
                userRepository,
                catalogExerciseRepository);
    }

    @Test
    void finishWorkoutBelowThirtyMinutesDoesNotIncrementHoneys() {
        WorkoutSession session = inProgressSession(LocalDateTime.of(2026, 6, 4, 10, 0));
        FinishWorkoutSnapshotRequestDTO request = finishRequest(session, session.getStartedAt().plusSeconds(1799));

        when(workoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(response(session));

        workoutSessionService.finishWorkout(userId, session.getId(), request);

        assertEquals(1, user.getWorkoutsCount());
        assertEquals(0, user.getOffensiveHoneys());
        assertEquals(0, user.getWeeklyWorkoutsCount());
    }

    @Test
    void finishWorkoutAtThirtyMinutesIncrementsHoneysEveryTime() {
        WorkoutSession firstSession = inProgressSession(LocalDateTime.of(2026, 6, 4, 10, 0));
        FinishWorkoutSnapshotRequestDTO firstRequest = finishRequest(
                firstSession,
                firstSession.getStartedAt().plusSeconds(1800));

        when(workoutSessionRepository.findById(firstSession.getId())).thenReturn(Optional.of(firstSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(response(firstSession));

        workoutSessionService.finishWorkout(userId, firstSession.getId(), firstRequest);

        WorkoutSession secondSession = inProgressSession(LocalDateTime.of(2026, 6, 4, 14, 0));
        FinishWorkoutSnapshotRequestDTO secondRequest = finishRequest(
                secondSession,
                secondSession.getStartedAt().plusSeconds(1800));

        when(workoutSessionRepository.findById(secondSession.getId())).thenReturn(Optional.of(secondSession));
        when(workoutSessionMapper.toDto(any(WorkoutSession.class))).thenReturn(response(secondSession));

        workoutSessionService.finishWorkout(userId, secondSession.getId(), secondRequest);

        assertEquals(2, user.getWorkoutsCount());
        assertEquals(2, user.getOffensiveHoneys());
        assertEquals(2, user.getWeeklyWorkoutsCount());
    }

    @Test
    void createTemplateReturnsTemplateIdAndCatalogExerciseSummary() {
        UUID catalogExerciseId = UUID.randomUUID();
        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(catalogExerciseId);
        catalogExercise.setName("Supino reto");

        WorkoutTemplateRequestDTO request = new WorkoutTemplateRequestDTO(
                "Push day",
                List.of(MuscleGroups.CHEST),
                List.of(new WorkoutTemplateExerciseRequestDTO(catalogExerciseId, 1)));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(catalogExerciseRepository.findById(catalogExerciseId)).thenReturn(Optional.of(catalogExercise));
        when(workoutTemplateRepository.save(any(WorkoutTemplate.class))).thenAnswer(invocation -> {
            WorkoutTemplate template = invocation.getArgument(0);
            template.setId(UUID.randomUUID());
            return template;
        });

        WorkoutTemplateResponseDTO response = workoutTemplateService.createTemplate(request, userId);

        assertNotNull(response.id());
        assertEquals("Push day", response.name());
        assertEquals(List.of(MuscleGroups.CHEST), response.muscleGroups());
        assertEquals(1, response.exercises().size());
        assertEquals(catalogExerciseId, response.exercises().get(0).catalogExerciseId());
        assertEquals("Supino reto", response.exercises().get(0).catalogExerciseName());
        assertEquals(1, response.exercises().get(0).order());
    }

    private WorkoutSession inProgressSession(LocalDateTime startedAt) {
        CatalogExercise catalogExercise = new CatalogExercise();
        catalogExercise.setId(UUID.randomUUID());
        catalogExercise.setName("Supino reto");

        SessionExercise sessionExercise = new SessionExercise();
        sessionExercise.setId(UUID.randomUUID());
        sessionExercise.setExercise(catalogExercise);
        sessionExercise.setOrder(1);

        Set set = new Set();
        set.setId(UUID.randomUUID());
        set.setOrder(1);
        set.setWeightKg(80.0);
        set.setReps(8);
        set.setIsPersonalRecord(false);
        sessionExercise.addSet(set);

        WorkoutSession session = new WorkoutSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setStartedAt(startedAt);
        session.setMuscleGroupsList(List.of(MuscleGroups.CHEST));
        session.setIsPrivate(false);
        session.setStatus(WorkoutStatus.IN_PROGRESS);
        session.addExercise(sessionExercise);
        return session;
    }

    private FinishWorkoutSnapshotRequestDTO finishRequest(WorkoutSession session, LocalDateTime finishedAt) {
        SessionExercise exercise = session.getExercises().get(0);
        Set set = exercise.getSets().get(0);

        return new FinishWorkoutSnapshotRequestDTO(
                finishedAt,
                List.of(MuscleGroups.CHEST),
                List.of(new FinishWorkoutExerciseDTO(
                        exercise.getId(),
                        exercise.getExercise().getId(),
                        1,
                        List.of(new FinishWorkoutSetDTO(
                                set.getId(),
                                1,
                                set.getWeightKg(),
                                set.getReps())))));
    }

    private WorkoutSessionResponseDTO response(WorkoutSession session) {
        return new WorkoutSessionResponseDTO(
                session.getId(),
                session.getStatus().getStatus(),
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getDurationSeconds(),
                session.getEstimatedCalories(),
                session.getTotalVolume(),
                List.of("peito"),
                session.getIsPrivate(),
                List.of());
    }
}
