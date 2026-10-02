package com.beefit.api.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.service.WorkoutGamificationService;
import com.beefit.api.service.WorkoutNotificationService;
import com.beefit.api.service.WorkoutPostService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkoutEventListener {

    private final WorkoutGamificationService workoutGamificationService;
    private final WorkoutPostService workoutPostService;
    private final WorkoutNotificationService workoutNotificationService;

    @Async("workoutTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkoutFinished(WorkoutFinishedEvent event) {
        log.info("Processando evento WorkoutFinishedEvent assincronamente para o treino {}", event.workoutSessionId());

        try {
            workoutGamificationService.processWorkoutFinished(event);
        } catch (Exception e) {
            log.error("Erro ao processar gamificação para o treino {}", event.workoutSessionId(), e);
        }

        try {
            workoutPostService.processWorkoutPost(event);
        } catch (Exception e) {
            log.error("Erro ao processar post no feed para o treino {}", event.workoutSessionId(), e);
        }

        try {
            workoutNotificationService.processWorkoutPresenceNotification(event);
        } catch (Exception e) {
            log.error("Erro ao transmitir notificações SSE para o treino {}", event.workoutSessionId(), e);
        }
    }
}
