package com.beefit.api.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.service.WorkoutGamificationService;
import com.beefit.api.service.WorkoutNotificationService;
import com.beefit.api.service.WorkoutPostService;

@ExtendWith(MockitoExtension.class)
class WorkoutEventListenerTest {

    @Mock
    private WorkoutGamificationService workoutGamificationService;

    @Mock
    private WorkoutPostService workoutPostService;

    @Mock
    private WorkoutNotificationService workoutNotificationService;

    @InjectMocks
    private WorkoutEventListener workoutEventListener;

    private WorkoutFinishedEvent event;

    @BeforeEach
    void setUp() {
        event = new WorkoutFinishedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now(),
                1800,
                true,
                "Treino Teste",
                true,
                false,
                UUID.randomUUID()
        );
    }

    @Test
    void onWorkoutFinishedDispatchesToAllServices() {
        workoutEventListener.onWorkoutFinished(event);

        verify(workoutGamificationService).processWorkoutFinished(event);
        verify(workoutPostService).processWorkoutPost(event);
        verify(workoutNotificationService).processWorkoutPresenceNotification(event);
    }

    @Test
    void onWorkoutFinishedHandlesExceptionsGracefully() {
        doThrow(new RuntimeException("Gamification error"))
                .when(workoutGamificationService).processWorkoutFinished(event);
        doThrow(new RuntimeException("Post error"))
                .when(workoutPostService).processWorkoutPost(event);
        doThrow(new RuntimeException("SSE error"))
                .when(workoutNotificationService).processWorkoutPresenceNotification(event);

        assertDoesNotThrow(() -> workoutEventListener.onWorkoutFinished(event));

        verify(workoutGamificationService).processWorkoutFinished(event);
        verify(workoutPostService).processWorkoutPost(event);
        verify(workoutNotificationService).processWorkoutPresenceNotification(event);
    }
}
