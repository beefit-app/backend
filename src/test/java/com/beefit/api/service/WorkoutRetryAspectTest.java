package com.beefit.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.infra.async.RetryConfig;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

@SpringBootTest(classes = {
        WorkoutGamificationService.class,
        WorkoutPostService.class,
        RetryConfig.class
})
@ActiveProfiles("test")
class WorkoutRetryAspectTest {

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private WorkoutSessionRepository workoutSessionRepository;
    @MockitoBean private CardService cardService;
    @MockitoBean private SseService sseService;
    @MockitoBean private WorkoutMuscleGroupMapper workoutMuscleGroupMapper;
    @MockitoBean private PostRepository postRepository;
    @MockitoBean private WorkoutMediaService workoutMediaService;

    @Autowired private WorkoutGamificationService workoutGamificationService;
    @Autowired private WorkoutPostService workoutPostService;

    @Test
    void shouldRetryOnTransientExceptionAndSucceed() {
        UUID userId = UUID.randomUUID();
        UUID workoutId = UUID.randomUUID();
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.now(), 3600, true, "Treino", false, false, null);

        User user = new User();
        user.setId(userId);
        user.setWorkoutsCount(0);

        WorkoutSession session = new WorkoutSession();
        session.setId(workoutId);
        session.setStatus(WorkoutStatus.FINISHED);

        // 1ª tentativa: CannotAcquireLockException (transitório)
        // 2ª tentativa: Sucesso
        when(userRepository.findById(userId))
                .thenThrow(new CannotAcquireLockException("Lock contention simulado"))
                .thenReturn(Optional.of(user));
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertDoesNotThrow(() -> workoutGamificationService.processWorkoutFinished(event));

        verify(userRepository, times(2)).findById(userId);
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void shouldNotRetryOnBusinessException() {
        UUID userId = UUID.randomUUID();
        UUID workoutId = UUID.randomUUID();
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.now(), 3600, true, "Treino", false, false, null);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> workoutGamificationService.processWorkoutFinished(event));

        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void shouldExhaustRetriesAndTriggerRecover() {
        UUID userId = UUID.randomUUID();
        UUID workoutId = UUID.randomUUID();
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.now(), 3600, true, "Treino", false, false, null);

        // 3 tentativas consecutivas falhando
        when(userRepository.findById(userId))
                .thenThrow(new CannotAcquireLockException("Deadlock persistente simulado"));

        assertDoesNotThrow(() -> workoutGamificationService.processWorkoutFinished(event));

        verify(userRepository, times(3)).findById(userId);
    }

    @Test
    void shouldRetryPostServiceOnTransientExceptionAndSucceed() {
        UUID userId = UUID.randomUUID();
        UUID workoutId = UUID.randomUUID();
        WorkoutFinishedEvent event = new WorkoutFinishedEvent(
                workoutId, userId, LocalDateTime.now(), 3600, true, "Treino Post", true, false, null);

        User user = new User();
        user.setId(userId);
        user.setGhostMode(false);

        WorkoutSession session = new WorkoutSession();
        session.setId(workoutId);

        when(postRepository.findByWorkoutSessionId(workoutId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId))
                .thenThrow(new CannotAcquireLockException("Lock temporário no post"))
                .thenReturn(Optional.of(user));
        when(workoutSessionRepository.findById(workoutId)).thenReturn(Optional.of(session));

        assertDoesNotThrow(() -> workoutPostService.processWorkoutPost(event));

        verify(userRepository, times(2)).findById(userId);
        verify(postRepository, times(1)).save(any());
    }
}
