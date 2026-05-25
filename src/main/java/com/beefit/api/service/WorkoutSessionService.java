package com.beefit.api.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.SessionExercise;
import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutSession;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.domain.enums.WorkoutStatus;
import com.beefit.api.dto.StartWorkoutExerciseItemDTO;
import com.beefit.api.dto.StartWorkoutRequestDTO;
import com.beefit.api.dto.WorkoutSessionResponseDTO;
import com.beefit.api.exception.CatalogExerciseNotFound;
import com.beefit.api.exception.InvalidWorkoutSessionStateException;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.exception.WorkoutAlreadyInProgressException;
import com.beefit.api.exception.WorkoutTemplateNotFoundException;
import com.beefit.api.mapper.WorkoutSessionMapper;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutSessionRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkoutSessionService {
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final CatalogExerciseRepository catalogExerciseRepository;
    private final UserRepository userRepository;
    private final WorkoutSessionMapper workoutSessionMapper;

    @Transactional
    public WorkoutSessionResponseDTO startWorkout(UUID userId, StartWorkoutRequestDTO request) {
        validateExerciseSource(request);
        validateNoWorkoutInProgress(userId);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());
        session.setFinishedAt(null);
        session.setDurationSeconds(null);
        session.setEstimatedCalories(null);
        session.setTotalVolume(null);
        session.setMuscleGroupsList(request.muscleGroups());
        session.setIsPrivate(request.isPrivate());
        session.setStatus(WorkoutStatus.IN_PROGRESS);

        List<SessionExercise> sessionExercises = resolveInitialExercises(request);

        for (SessionExercise sessionExercise : sessionExercises) {
            session.addExercise(sessionExercise);
        }

        WorkoutSession savedSession = workoutSessionRepository.save(session);

        return workoutSessionMapper.toDto(savedSession);
    }

    // Esse método é o responsável por definir como o treino ativo trata os
    // exercícios,
    // ou seja, caso haja um template pré-definido, o treino já mostra os exercícios
    // corretamente
    private List<SessionExercise> resolveInitialExercises(StartWorkoutRequestDTO request) {
        if (request.templateId() == null) {
            return buildExercisesFromRequest(request.exercises());
        }
        return buildExercisesFromTemplate(request.templateId());
    }

    private List<SessionExercise> buildExercisesFromTemplate(UUID templateId) {
        WorkoutTemplate template = workoutTemplateRepository.findById(templateId)
                .orElseThrow(WorkoutTemplateNotFoundException::new);

        List<SessionExercise> exercises = new ArrayList<>();
        int order = 1;

        for (CatalogExercise exercise : template.getExerciseList()) {
            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(exercise);
            sessionExercise.setOrder(order++);
            exercises.add(sessionExercise);
        }

        return exercises;
    }

    private List<SessionExercise> buildExercisesFromRequest(List<StartWorkoutExerciseItemDTO> items) {
        List<SessionExercise> exercises = new ArrayList<>();

        for (StartWorkoutExerciseItemDTO item : items) {
            CatalogExercise catalogExercise = catalogExerciseRepository.findById(item.catalogExerciseId())
                    .orElseThrow(CatalogExerciseNotFound::new);
            SessionExercise sessionExercise = new SessionExercise();
            sessionExercise.setExercise(catalogExercise);
            sessionExercise.setOrder(item.order());
            exercises.add(sessionExercise);
        }

        return exercises;
    }

    private void validateExerciseSource(StartWorkoutRequestDTO request) {
        boolean hasTemplate = request.templateId() != null;
        boolean hasExercises = request.exercises() != null;

        if (hasTemplate == hasExercises) {
            throw new InvalidWorkoutSessionStateException();
        }
    }

    private void validateNoWorkoutInProgress(UUID userId) {
        boolean workoutInProgress = workoutSessionRepository.findByStatusAndUserId(WorkoutStatus.IN_PROGRESS, userId)
                .stream().findAny().isPresent();

        if (workoutInProgress) {
            throw new WorkoutAlreadyInProgressException();
        }
    }
}
