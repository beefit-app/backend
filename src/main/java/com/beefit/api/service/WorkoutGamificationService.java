package com.beefit.api.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.Card;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.enums.SocialEvent;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.social.SocialEventDTO;
import com.beefit.api.dto.workout.CardGeneratedEventDTO;
import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.exception.workout.WorkoutSessionNotFoundException;
import com.beefit.api.mapper.WorkoutMuscleGroupMapper;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutGamificationService {

    public static final int MIN_HONEY_WORKOUT_DURATION_SECONDS = 1740;

    private final UserRepository userRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final CardService cardService;
    private final SseService sseService;
    private final WorkoutMuscleGroupMapper workoutMuscleGroupMapper;

    @Retryable(
            retryFor = {
                    TransientDataAccessException.class,
                    ConcurrencyFailureException.class,
                    CannotCreateTransactionException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processWorkoutFinished(WorkoutFinishedEvent event) {
        log.info("Iniciando processWorkoutFinished para o user {} e treino {}", event.userId(), event.workoutSessionId());
        User user = userRepository.findById(event.userId())
                .orElseThrow(UserNotFoundException::new);

        WorkoutSession session = workoutSessionRepository.findById(event.workoutSessionId())
                .orElseThrow(WorkoutSessionNotFoundException::new);

        user.setWorkoutsCount((user.getWorkoutsCount() == null ? 0 : user.getWorkoutsCount()) + 1);

        LocalDateTime honeyDayStart = event.finishedAt().toLocalDate().atStartOfDay();
        boolean eligibleForHoney = event.durationSeconds() >= MIN_HONEY_WORKOUT_DURATION_SECONDS
                && event.hasCompletedSets();

        boolean alreadyEarnedHoneyToday = eligibleForHoney
                && workoutSessionRepository.existsEligibleFinishedWorkoutOnDay(
                        event.userId(),
                        event.workoutSessionId(),
                        WorkoutStatus.FINISHED,
                        MIN_HONEY_WORKOUT_DURATION_SECONDS,
                        honeyDayStart,
                        honeyDayStart.plusDays(1));

        if (eligibleForHoney && !alreadyEarnedHoneyToday) {
            user.setOffensiveHoneys((user.getOffensiveHoneys() == null ? 0 : user.getOffensiveHoneys()) + 1);
            user.setWeeklyWorkoutsCount((user.getWeeklyWorkoutsCount() == null ? 0 : user.getWeeklyWorkoutsCount()) + 1);
        }

        Card card = cardService.generateCard(user, session);
        if (card != null) {
            session.setCard(card);
            workoutSessionRepository.save(session);
        }

        userRepository.save(user);
        log.info("Finalizado processWorkoutFinished com sucesso para o user {} (workoutsCount={})", user.getId(), user.getWorkoutsCount());

        if (card != null) {
            broadcastCardGenerated(event.userId(), session, card);
        }
    }

    @Recover
    public void recoverWorkoutFinished(Throwable ex, WorkoutFinishedEvent event) throws Throwable {
        if (ex instanceof TransientDataAccessException || ex instanceof ConcurrencyFailureException
                || ex instanceof CannotCreateTransactionException) {
            log.error("CRÍTICO: Todas as tentativas de retry esgotadas ao processar gamificação do treino {}. Erro: {}",
                    event.workoutSessionId(), ex.getMessage(), ex);
            return;
        }
        throw ex;
    }

    private void broadcastCardGenerated(UUID userId, WorkoutSession session, Card card) {
        sseService.broadcastToFriend(
                new SocialEventDTO<>(
                        SocialEvent.CARD_GENERATED,
                        new CardGeneratedEventDTO(
                                session.getId(),
                                card.getId(),
                                card.getEstimatedOneRepMax() == null
                                        ? card.getBestSetReps().doubleValue()
                                        : card.getEstimatedOneRepMax(),
                                card.getCardRarity().name(),
                                workoutMuscleGroupMapper.toApi(card.getMuscleGroup()),
                                card.getMuscleIntensities(),
                                card.getImprovementPercentage() > 0
                        )
                ),
                userId
        );
    }
}
