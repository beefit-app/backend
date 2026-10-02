package com.beefit.api.service;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.Post;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.event.workout.WorkoutFinishedEvent;
import com.beefit.api.exception.user.UserNotFoundException;
import com.beefit.api.exception.workout.WorkoutSessionNotFoundException;
import com.beefit.api.repository.PostRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutPostService {

    private final UserRepository userRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final PostRepository postRepository;
    private final WorkoutMediaService workoutMediaService;

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
    public void processWorkoutPost(WorkoutFinishedEvent event) {
        if (!event.shouldPostWorkout() || event.isPrivate()) {
            return;
        }

        if (postRepository.findByWorkoutSessionId(event.workoutSessionId()).isPresent()) {
            log.info("Post já existente para o treino {}, ignorando reprocessamento.", event.workoutSessionId());
            return;
        }

        User user = userRepository.findById(event.userId())
                .orElseThrow(UserNotFoundException::new);

        if (Boolean.TRUE.equals(user.getGhostMode())) {
            return;
        }

        WorkoutSession session = workoutSessionRepository.findById(event.workoutSessionId())
                .orElseThrow(WorkoutSessionNotFoundException::new);

        Post autoPost = new Post();
        autoPost.setUser(user);
        autoPost.setWorkoutSession(session);
        autoPost.setTotalLikes(0);
        autoPost.setIsRemoved(false);
        autoPost.setTitle(event.title());

        if (event.photoMediaId() != null) {
            workoutMediaService.consume(event.photoMediaId(), event.userId());
            autoPost.setPhotoMediaId(event.photoMediaId());
        }

        postRepository.save(autoPost);
    }

    @Recover
    public void recoverWorkoutPost(Throwable ex, WorkoutFinishedEvent event) throws Throwable {
        if (ex instanceof TransientDataAccessException || ex instanceof ConcurrencyFailureException
                || ex instanceof CannotCreateTransactionException) {
            log.error("CRÍTICO: Todas as tentativas de retry esgotadas ao criar post no feed para o treino {}. Erro: {}",
                    event.workoutSessionId(), ex.getMessage(), ex);
            return;
        }
        throw ex;
    }
}
